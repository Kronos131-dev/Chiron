import { Signal, computed, signal } from '@angular/core';
import { timeout } from 'rxjs';
import { CourseTracker, allureKmh, tempsALaDistanceS } from './course-tracker';
import { CourseRuntime, OptionsCourse, interpoler } from './course-runtime';
import { formaterDistance, kmhVersMinParKm, minParKmVersKmh } from '../util/allure';
import { Commande, interpreter } from '../util/commandes-vocales';
import { Voix, creerVoix, voixDisponible } from '../util/voix';
import { Survie, tenirEnVie } from '../util/survie-arriere-plan';
import { baisserLaMusiquePendant } from '../util/session-audio';
import { ChironApi } from './chiron-api';

const TICK_MS = 1000;
const KM_EN_METRES = 1000;
const SECONDES_PAR_MINUTE = 60;
const CIBLE_DEFAUT_MIN_PAR_KM = 6;
const CIBLE_MIN = 2.5;
const CIBLE_MAX = 15;
const PAS_CIBLE_MIN_PAR_KM = 5 / SECONDES_PAR_MINUTE;
const ECART_TOLERE_MIN_PAR_KM = 0.25;
const ECART_FRANC_MIN_PAR_KM = 0.6;
const DUREE_ECART_MS = 15000;
const SILENCE_ENTRE_ANNONCES_MS = 60000;

export class RuntimeWeb implements CourseRuntime {
  readonly natif = false;

  private readonly tracker = new CourseTracker();
  private chironApi: ChironApi | null = null;

  readonly etat = this.tracker.etat;
  readonly points = this.tracker.points;
  readonly splits = this.tracker.splits;
  readonly distanceM = this.tracker.distanceM;
  readonly dureeS = this.tracker.dureeS;
  readonly allureCouranteKmh = this.tracker.allureCouranteKmh;
  readonly allureMoyenneKmh = this.tracker.allureMoyenneKmh;
  readonly precisionM = this.tracker.precisionM;
  readonly erreurGps = this.tracker.erreurGps;
  readonly signalPerdu = this.tracker.signalPerdu;

  readonly ecoute = signal(false);
  readonly transcript = signal('');
  readonly commandeComprise = signal<boolean | null>(null);
  readonly erreurMicro = signal<string | null>(null);
  readonly voixMuette = signal(false);
  readonly objectifDureeS = signal(0);
  readonly audioActif = signal(true);
  // WHY: le mot-clé demande une reconnaissance embarquée qui tourne écran verrouillé. Le
  // navigateur n'en a pas : le sien passe par le réseau et s'éteint avec l'écran. La PWA garde
  // donc l'appui-pour-parler, et le dit plutôt que de laisser croire à une panne.
  readonly motCleActif = signal(false);
  readonly motCleIndisponible = signal<string | null>('navigateur');
  readonly microDisponible: Signal<boolean> = computed(() => this.moteurReconnaissance() !== null);

  private readonly enCours = computed(() => this.etat() === 'enCours');
  private readonly enPause = computed(() => this.etat() === 'enPause');

  private options: OptionsCourse | null = null;
  private cible: number | null = null;
  private survie: Survie | null = null;
  private voix: Voix | null = null;
  private reconnaissance: any = null;
  private ticker: ReturnType<typeof setInterval> | null = null;

  private paliersAnnonces = 0;
  private objectifAnnonce = false;
  private ecartDepuis: number | null = null;
  private derniereAnnonceEcart = 0;

  attacher(routineId: string, exoId: string): void {
    this.tracker.attacher(routineId, exoId);
    if (this.ticker === null) this.ticker = setInterval(() => this.tick(), TICK_MS);
  }

  async reprendreCourseEnCours(): Promise<boolean> {
    const reprise = this.tracker.restaurer();
    const snapshot = this.tracker.lireSnapshot();
    this.cible = snapshot?.cibleMinParKm ?? null;
    this.paliersAnnonces = snapshot?.paliersAnnonces ?? 0;
    return reprise && this.enCours();
  }

  cibleRetenue(): number | null {
    return this.cible;
  }

  configurer(options: OptionsCourse): void {
    this.options = options;
    this.voix?.fixerVolume(this.fractionDeVolume());
  }

  setChironApi(api: ChironApi): void {
    this.chironApi = api;
  }

  configurerApiCommandes(baseUrl: string, token: string): void {}

  async demarrer(): Promise<void> {
    this.activerLeSon();
    if (this.etat() !== 'pret') return;
    this.cible = this.options?.cibleMinParKm ?? null;
    this.tracker.demarrer();
    this.tracker.ecrireSnapshot({ cibleMinParKm: this.cible, paliersAnnonces: 0 });
    this.dire(this.phrase('started'), false);
  }

  basculerPause(): void {
    if (this.etat() === 'termine' || this.etat() === 'pret') return;
    this.activerLeSon();
    this.tracker.basculerPause();
    this.dire(this.phrase(this.enCours() ? 'resumed' : 'paused'), false);
  }

  async arreter(): Promise<void> {
    this.tracker.arreter();
    this.survie?.relacher();
    this.survie = null;
    this.arreterEcoute();
    this.dire(this.phrase('finished'), true);
  }

  fixerCible(minParKm: number | null): void {
    if (minParKm === null) {
      this.cible = null;
      this.tracker.ecrireSnapshot({ cibleMinParKm: null });
      return;
    }
    const bornee = Math.min(CIBLE_MAX, Math.max(CIBLE_MIN, minParKm));
    this.cible = bornee;
    this.tracker.ecrireSnapshot({ cibleMinParKm: bornee });
    this.ecartDepuis = null;
    this.dire(
      interpoler(this.phrase('target'), {
        cible: this.allureParlee(minParKmVersKmh(bornee)),
      }),
      true,
    );
  }

  dire(texte: string, prioritaire: boolean): void {
    if (!this.voix || !texte) return;
    if (prioritaire) this.voix.interrompreEtParler(texte);
    else this.voix.parler(texte);
  }

  // WHY: le réglage du volume vit avant le départ, quand aucune voix n'a encore été créée par
  // le geste de démarrage. L'essai la fabrique donc à la demande, faute de quoi le curseur se
  // réglerait à l'aveugle — précisément ce qu'il s'agit d'éviter.
  essayerVoix(texte: string): void {
    if (!texte) return;
    if (!this.voix && voixDisponible()) {
      this.voix = creerVoix(
        this.options?.langue === 'en' ? 'en-US' : 'fr-FR',
        baisserLaMusiquePendant,
      );
    }
    this.voix?.fixerVolume(this.fractionDeVolume());
    this.voix?.interrompreEtParler(texte);
  }

  private fractionDeVolume(): number {
    return Math.min(1, Math.max(0, (this.options?.volumeVoix ?? 100) / 100));
  }

  purger(): void {
    this.tracker.purgerSnapshot();
  }

  liberer(): void {
    if (this.ticker !== null) clearInterval(this.ticker);
    this.ticker = null;
    this.tracker.liberer();
    this.arreterEcoute();
    this.voix?.taire();
    this.survie?.relacher();
  }

  private tick(): void {
    this.tracker.rafraichir(Date.now());
    if (this.survie) this.audioActif.set(this.survie.audioEnLecture());
    if (!this.enCours()) return;
    this.annoncerLObjectif();
    this.annoncerLesPaliers();
    this.surveillerAllure();
  }

  // WHY: l'objectif est dit une fois et la course ne s'arrête pas. L'athlète a demandé dix
  // kilomètres, il veut savoir en combien il les a bouclés — et rester libre d'en courir deux
  // de plus sans que l'application décide à sa place que c'est fini.
  private annoncerLObjectif(): void {
    const objectif = this.options?.objectifDistanceM ?? 0;
    if (this.objectifAnnonce || objectif <= 0) return;
    const atteint = tempsALaDistanceS(this.points(), objectif);
    if (atteint === null) return;

    this.objectifAnnonce = true;
    this.objectifDureeS.set(atteint);
    this.dire(
      interpoler(this.phrase('goalReached'), {
        km: formaterDistance(objectif),
        temps: this.dureeParlee(atteint),
      }),
      true,
    );
  }

  // WHY: après un retour d'arrière-plan plusieurs paliers ont pu tomber d'un tick au suivant.
  // Seul le dernier est annoncé : les enchaîner à une seconde d'intervalle n'apprend rien et
  // couvre le palier en cours.
  private annoncerLesPaliers(): void {
    const intervalle = this.intervalleAnnonceM();
    const franchis = Math.floor(this.distanceM() / intervalle);
    if (franchis <= this.paliersAnnonces) return;
    this.paliersAnnonces = franchis;
    this.tracker.ecrireSnapshot({ paliersAnnonces: franchis });

    // WHY: c'est l'allure du palier qui vient d'être bouclé qui apprend quelque chose, pas la
    // moyenne depuis le départ — celle-ci se lisse et cesse de réagir après une demi-heure.
    // Les deux bornes sont recalculées sur la trace plutôt que retenues d'une annonce à
    // l'autre : les coordonnées sont arrondies à cinq décimales, soit un mètre, et un palier
    // frôlé par en dessous est sauté — la borne mémorisée serait alors celle d'un autre palier.
    const instant = tempsALaDistanceS(this.points(), franchis * intervalle);
    const precedent =
      franchis > 1 ? tempsALaDistanceS(this.points(), (franchis - 1) * intervalle) : 0;
    const dureePalier =
      instant === null || precedent === null ? 0 : Math.max(0, instant - precedent);

    this.dire(
      interpoler(this.phrase('km'), {
        distance: this.distanceParlee(franchis * intervalle),
        temps: this.dureeParlee(this.dureeS()),
        allure: this.allureParlee(
          dureePalier > 0 ? allureKmh(intervalle, dureePalier) : this.allureMoyenneKmh(),
        ),
      }),
      false,
    );
  }

  private intervalleAnnonceM(): number {
    const regle = this.options?.intervalleAnnonceM ?? KM_EN_METRES;
    return regle > 0 ? regle : KM_EN_METRES;
  }

  // WHY: sous le kilomètre, « 0.60 kilomètres » est illisible à l'oreille. L'annonce se dit
  // donc en mètres tant qu'on est en dessous, et le singulier existe parce que « 1 kilomètres »
  // s'entend, même prononcé par une machine.
  private distanceParlee(metres: number): string {
    if (metres < KM_EN_METRES) {
      return interpoler(this.phrase('metres'), { m: Math.round(metres) });
    }
    const km = metres / KM_EN_METRES;
    return interpoler(this.phrase(km === 1 ? 'kilometre' : 'kilometres'), {
      km: Number.isInteger(km) ? km : km.toFixed(1),
    });
  }

  // WHY: une annonce déclenchée au premier écart mesuré se répéterait à chaque tick sous un
  // pont ou dans une côte. L'écart doit tenir quinze secondes, et le coach se tait une minute
  // après avoir parlé — sinon l'athlète coupe le son et n'entend plus rien du tout.
  private surveillerAllure(): void {
    const ecart = this.ecartAllure();
    const maintenant = Date.now();

    if (ecart === null || Math.abs(ecart) <= ECART_TOLERE_MIN_PAR_KM) {
      this.ecartDepuis = null;
      return;
    }
    if (this.ecartDepuis === null) {
      this.ecartDepuis = maintenant;
      return;
    }
    if (maintenant - this.ecartDepuis < DUREE_ECART_MS) return;
    if (maintenant - this.derniereAnnonceEcart < SILENCE_ENTRE_ANNONCES_MS) return;

    this.derniereAnnonceEcart = maintenant;
    this.ecartDepuis = null;
    const franc = Math.abs(ecart) >= ECART_FRANC_MIN_PAR_KM;
    const cle =
      ecart > 0 ? (franc ? 'speedUp' : 'speedUpABit') : franc ? 'slowDown' : 'slowDownABit';
    this.dire(this.phrase(cle), false);
  }

  private ecartAllure(): number | null {
    const courante = kmhVersMinParKm(this.allureCouranteKmh());
    if (this.cible === null || courante === null || !this.enCours()) return null;
    return courante - this.cible;
  }

  // WHY: le contexte audio et la boucle de survie doivent naître dans le geste utilisateur.
  // Créés plus tard, Chrome les refuse : la page se ferait geler écran éteint et le coach
  // resterait muet pour toute la sortie.
  private activerLeSon(): void {
    if (this.survie || !this.options) return;
    // WHY: le flux inaudible qui empêche Chrome de geler la page prend le focus sonore, donc
    // interrompt la musique. C'était un arbitrage offert en réglage tant que la PWA était la
    // seule voie ; l'application Android n'a pas ce dilemme, et une sortie gelée en chemin est
    // pire qu'une musique coupée.
    this.survie = tenirEnVie(['audioElement', 'webLock'], false);
    this.audioActif.set(this.survie.audioEnLecture());
    if (!this.voix && voixDisponible()) {
      this.voix = creerVoix(
        this.options.langue === 'en' ? 'en-US' : 'fr-FR',
        baisserLaMusiquePendant,
      );
    }
    this.voix?.fixerVolume(this.fractionDeVolume());
  }

  private phrase(cle: string): string {
    return this.options?.phrases[cle] ?? '';
  }

  private dureeParlee(secondes: number): string {
    return interpoler(this.phrase('duration'), {
      minutes: Math.floor(secondes / SECONDES_PAR_MINUTE),
      secondes: secondes % SECONDES_PAR_MINUTE,
    });
  }

  private allureParlee(kmh: number): string {
    const minParKm = kmhVersMinParKm(kmh);
    if (minParKm === null) return this.phrase('noPace');
    if (this.options?.uniteAllure === 'kmh') {
      return interpoler(this.phrase('speed'), { vitesse: kmh.toFixed(1).replace('.', ',') });
    }
    const minutes = Math.floor(minParKm);
    return interpoler(this.phrase('pace'), {
      minutes,
      secondes: Math.round((minParKm - minutes) * SECONDES_PAR_MINUTE),
    });
  }

  private moteurReconnaissance(): any | null {
    if (typeof window === 'undefined') return null;
    const global = window as any;
    return global.SpeechRecognition ?? global.webkitSpeechRecognition ?? null;
  }

  // WHY: un appui-parle, pas une bascule. Avec continuous=false le moteur coupe l'écoute dès
  // le premier blanc, donc avant même que l'athlète essoufflé n'ait commencé sa phrase ; et
  // les résultats intermédiaires sont conservés parce que le résultat final n'arrive jamais
  // quand on relâche au milieu d'un mot.
  commencerEcoute(): void {
    if (this.ecoute()) return;
    const Moteur = this.moteurReconnaissance();
    if (!Moteur) return;

    this.activerLeSon();
    this.voix?.taire();
    this.transcript.set('');
    this.commandeComprise.set(null);
    this.erreurMicro.set(null);

    const moteur = new Moteur();
    moteur.lang = this.options?.langue === 'en' ? 'en-US' : 'fr-FR';
    moteur.continuous = true;
    moteur.interimResults = true;
    moteur.maxAlternatives = 3;

    moteur.onresult = (evenement: any) => {
      let entendu = '';
      for (let i = 0; i < evenement.results.length; i++) {
        entendu += ' ' + String(evenement.results[i][0].transcript);
      }
      this.transcript.set(entendu.trim());
    };
    moteur.onerror = () => this.terminerEcoute();
    moteur.onend = () => {
      if (this.ecoute()) this.terminerEcoute();
    };

    this.reconnaissance = moteur;
    this.ecoute.set(true);
    try {
      moteur.start();
    } catch {
      this.ecoute.set(false);
    }
  }

  terminerEcoute(): void {
    if (!this.ecoute()) return;
    this.ecoute.set(false);
    if (this.reconnaissance) {
      try {
        this.reconnaissance.stop();
      } catch {}
      this.reconnaissance = null;
    }
    this.interpreterCommande(this.transcript());
  }

  private arreterEcoute(): void {
    if (this.reconnaissance) {
      try {
        this.reconnaissance.stop();
      } catch {}
      this.reconnaissance = null;
    }
    this.ecoute.set(false);
  }

  private interpreterCommande(transcript: string): void {
    if (!this.chironApi) {
      this.interpreterLocalement(transcript);
      return;
    }

    this.chironApi
      .interpretVoiceCommand(transcript, this.options?.langue === 'en' ? 'en' : 'fr')
      .pipe(timeout(3000))
      .subscribe({
        next: (response: any) => {
          if (!response || !response.nom) {
            this.interpreterLocalement(transcript);
            return;
          }
          const commande: Commande = {
            nom: response.nom as any,
            cibleMinParKm: response.cibleMinParKm,
          };
          this.commandeComprise.set(true);
          this.executer(commande);
        },
        error: () => this.interpreterLocalement(transcript),
      });
  }

  private interpreterLocalement(transcript: string): void {
    const commande = interpreter(transcript);
    if (!commande) {
      this.commandeComprise.set(false);
      this.dire(this.phrase('notUnderstood'), true);
      return;
    }
    this.commandeComprise.set(true);
    this.executer(commande);
  }

  executer(commande: Commande): void {
    switch (commande.nom) {
      case 'pause':
        if (this.enCours()) this.basculerPause();
        return;
      case 'reprendre':
        if (this.enPause()) this.basculerPause();
        return;
      case 'plusVite':
        this.fixerCible((this.cible ?? CIBLE_DEFAUT_MIN_PAR_KM) - PAS_CIBLE_MIN_PAR_KM);
        return;
      case 'moinsVite':
        this.fixerCible((this.cible ?? CIBLE_DEFAUT_MIN_PAR_KM) + PAS_CIBLE_MIN_PAR_KM);
        return;
      case 'cible':
        if (commande.cibleMinParKm !== undefined) this.fixerCible(commande.cibleMinParKm);
        return;
      case 'allure':
        this.dire(this.allureParlee(this.allureCouranteKmh()), true);
        return;
      case 'distance':
        this.dire(
          interpoler(this.phrase('distance'), { km: formaterDistance(this.distanceM()) }),
          true,
        );
        return;
      case 'duree':
        this.dire(this.dureeParlee(this.dureeS()), true);
        return;
      case 'bilan':
        this.dire(
          interpoler(this.phrase('summary'), {
            km: formaterDistance(this.distanceM()),
            temps: this.dureeParlee(this.dureeS()),
            allure: this.allureParlee(this.allureCouranteKmh()),
          }),
          true,
        );
        return;
    }
  }
}
