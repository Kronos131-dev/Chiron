import { Signal, computed, signal } from '@angular/core';
import { CourseTracker } from './course-tracker';
import { CourseRuntime, OptionsCourse, interpoler } from './course-runtime';
import { formaterDistance, kmhVersMinParKm, minParKmVersKmh } from '../util/allure';
import { Commande, interpreter } from '../util/commandes-vocales';
import {
  ActionCasque,
  MappingCasque,
  Telecommande,
  brancherTelecommande,
} from '../util/telecommande-casque';
import { Voix, creerVoix, voixDisponible } from '../util/voix';
import { Survie, tenirEnVie } from '../util/survie-arriere-plan';
import { baisserLaMusiquePendant } from '../util/session-audio';

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
const ECOUTE_MAINS_LIBRES_MS = 7000;

export class RuntimeWeb implements CourseRuntime {
  readonly natif = false;

  private readonly tracker = new CourseTracker();

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
  readonly audioActif = signal(true);
  readonly microDisponible: Signal<boolean> = computed(() => this.moteurReconnaissance() !== null);

  private readonly enCours = computed(() => this.etat() === 'enCours');
  private readonly enPause = computed(() => this.etat() === 'enPause');

  private options: OptionsCourse | null = null;
  private cible: number | null = null;
  private survie: Survie | null = null;
  private voix: Voix | null = null;
  private telecommande: Telecommande | null = null;
  private reconnaissance: any = null;
  private ticker: ReturnType<typeof setInterval> | null = null;
  private minuterieEcoute: ReturnType<typeof setTimeout> | null = null;

  private kmAnnonces = 0;
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
    this.kmAnnonces = snapshot?.kmAnnonces ?? 0;
    return reprise && this.enCours();
  }

  cibleRetenue(): number | null {
    return this.cible;
  }

  configurer(options: OptionsCourse): void {
    this.options = options;
    if (!this.telecommande) return;
    this.telecommande.relacher();
    this.telecommande = brancherTelecommande(options.appuiCourt, (action) =>
      this.executerAction(action),
    );
    this.telecommande.annoncerEnCours(this.titre(), this.enCours() ? 'playing' : 'paused');
  }

  async demarrer(): Promise<void> {
    this.activerLeSon();
    if (this.etat() !== 'pret') return;
    this.cible = this.options?.cibleMinParKm ?? null;
    this.tracker.demarrer();
    this.tracker.ecrireSnapshot({ cibleMinParKm: this.cible, kmAnnonces: 0 });
    this.dire(this.phrase('started'), false);
  }

  basculerPause(): void {
    if (this.etat() === 'termine' || this.etat() === 'pret') return;
    this.activerLeSon();
    this.tracker.basculerPause();
    this.telecommande?.annoncerEnCours(this.titre(), this.enCours() ? 'playing' : 'paused');
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

  purger(): void {
    this.tracker.purgerSnapshot();
  }

  liberer(): void {
    if (this.ticker !== null) clearInterval(this.ticker);
    this.ticker = null;
    if (this.minuterieEcoute) clearTimeout(this.minuterieEcoute);
    this.tracker.liberer();
    this.arreterEcoute();
    this.telecommande?.relacher();
    this.voix?.taire();
    this.survie?.relacher();
  }

  private tick(): void {
    this.tracker.rafraichir(Date.now());
    if (this.survie) this.audioActif.set(this.survie.audioEnLecture());
    if (!this.enCours()) return;
    this.annoncerKilometres();
    this.surveillerAllure();
  }

  // WHY: après un retour d'arrière-plan plusieurs kilomètres ont pu tomber d'un tick au
  // suivant. Seul le dernier est annoncé : entendre « kilomètre 3 » puis « kilomètre 4 » à une
  // seconde d'intervalle n'apprend rien et couvre le kilomètre en cours.
  private annoncerKilometres(): void {
    const franchis = Math.floor(this.distanceM() / KM_EN_METRES);
    if (franchis <= this.kmAnnonces) return;
    this.kmAnnonces = franchis;
    this.tracker.ecrireSnapshot({ kmAnnonces: franchis });
    this.dire(
      interpoler(this.phrase('km'), {
        km: franchis,
        temps: this.dureeParlee(this.dureeS()),
        allure: this.allureParlee(this.allureMoyenneKmh()),
      }),
      false,
    );
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
    this.survie = tenirEnVie(['audioElement', 'webLock'], this.options.melangerMusique);
    this.audioActif.set(this.survie.audioEnLecture());
    this.voix = voixDisponible()
      ? creerVoix(this.options.langue === 'en' ? 'en-US' : 'fr-FR', baisserLaMusiquePendant)
      : null;
    this.telecommande = brancherTelecommande(this.options.appuiCourt, (action) =>
      this.executerAction(action),
    );
    this.telecommande.annoncerEnCours(this.titre(), 'playing');
  }

  private titre(): string {
    return this.options?.titre ?? 'Chiron';
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

    this.voix?.taire();
    this.transcript.set('');
    this.commandeComprise.set(null);

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
    if (this.minuterieEcoute) {
      clearTimeout(this.minuterieEcoute);
      this.minuterieEcoute = null;
    }
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

  private executerAction(action: ActionCasque): void {
    if (action === 'rien') return;
    if (action === 'ecouter') {
      this.ecouterMainsLibres();
      return;
    }
    if (action === 'pause') {
      this.basculerPause();
      return;
    }
    this.executer({ nom: action });
  }

  // WHY: un bouton de casque n'envoie qu'une impulsion — mediaSession ne dit ni si l'appui a
  // duré, ni quand il cesse. Impossible d'en faire un vrai maintiens-et-parle : l'écoute
  // s'ouvre donc à l'impulsion et se referme d'elle-même, au silence ou au bout du délai.
  ecouterMainsLibres(): void {
    if (this.ecoute()) {
      this.terminerEcoute();
      return;
    }
    this.dire(this.phrase('listening'), true);
    this.commencerEcoute();
    if (this.minuterieEcoute) clearTimeout(this.minuterieEcoute);
    this.minuterieEcoute = setTimeout(() => this.terminerEcoute(), ECOUTE_MAINS_LIBRES_MS);
  }
}
