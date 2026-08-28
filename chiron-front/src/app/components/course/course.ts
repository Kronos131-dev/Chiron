import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { ActiveSessionService } from '../../service/active-session.service';
import { ChironApi, CoursePointDto, CourseTraceDto } from '../../service/chiron-api';
import { CourseTracker } from '../../service/course-tracker';
import { I18nService } from '../../service/i18n.service';
import { TranslatePipe } from '../../service/translate.pipe';
import { HeaderComponent } from '../shared/header/header';
import { ExerciceForm } from '../../shared/exercise-forms';
import {
  formaterAllure,
  formaterChrono,
  formaterDistance,
  kmhVersMinParKm,
  minParKmVersKmh,
} from '../../util/allure';
import { Survie, tenirEnVie } from '../../util/survie-arriere-plan';
import { Voix, creerVoix, voixDisponible } from '../../util/voix';
import {
  ACTIONS_CASQUE,
  ActionCasque,
  BOUTONS_CASQUE,
  BoutonCasque,
  MAPPING_PAR_DEFAUT,
  MappingCasque,
  Telecommande,
  brancherTelecommande,
} from '../../util/telecommande-casque';
import { Commande, interpreter, lireAllure } from '../../util/commandes-vocales';
import { baisserLaMusiquePendant } from '../../util/session-audio';
import { TraceSvg, projeterTrace } from '../../util/trace-svg';

const TICK_MS = 1000;
const COTE_TRACE = 320;
const KM_EN_METRES = 1000;
const SECONDES_PAR_MINUTE = 60;

const CIBLE_DEFAUT_MIN_PAR_KM = 6;
const CIBLE_MIN = 2.5;
const CIBLE_MAX = 15;
const PAS_CIBLE_MIN_PAR_KM = 5 / SECONDES_PAR_MINUTE;

const ECART_TOLERE_MIN_PAR_KM = 0.25;
const DUREE_ECART_MS = 15000;
const SILENCE_ENTRE_ANNONCES_MS = 60000;
const CLE_MAPPING_CASQUE = 'chiron.course.casque';
const CLE_MELANGER_MUSIQUE = 'chiron.course.melangerMusique';
const ECART_FRANC_MIN_PAR_KM = 0.6;
const ECOUTE_MAINS_LIBRES_MS = 7000;

@Component({
  selector: 'app-course',
  standalone: true,
  imports: [CommonModule, FormsModule, HeaderComponent, TranslatePipe],
  templateUrl: './course.html',
  styleUrl: './course.css',
})
export class Course implements OnInit, OnDestroy {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private activeSession = inject(ActiveSessionService);
  private chironApi = inject(ChironApi);
  private i18n = inject(I18nService);

  readonly tracker = new CourseTracker();

  routineId = '';
  exoId = '';
  exercice: ExerciceForm | null = null;

  readonly cibleMinParKm = signal<number | null>(null);
  readonly cibleSaisie = signal('');
  readonly ecoute = signal(false);
  readonly transcript = signal('');
  readonly commandeComprise = signal<boolean | null>(null);
  readonly reglagesOuverts = signal(false);
  readonly melangerMusique = signal(false);
  readonly audioActif = signal(true);
  readonly mappingCasque = signal<MappingCasque>({ ...MAPPING_PAR_DEFAUT });
  readonly boutonsCasque = BOUTONS_CASQUE;
  readonly actionsCasque = ACTIONS_CASQUE;
  readonly enregistrement = signal(false);
  readonly erreurEnregistrement = signal(false);
  readonly resume = signal<CourseTraceDto | null>(null);
  readonly microDisponible = signal(false);

  private survie: Survie | null = null;
  private voix: Voix | null = null;
  private telecommande: Telecommande | null = null;
  private reconnaissance: any = null;
  private ticker: ReturnType<typeof setInterval> | null = null;
  private minuterieEcoute: ReturnType<typeof setTimeout> | null = null;

  private kmAnnonces = 0;
  private ecartDepuis: number | null = null;
  private derniereAnnonceEcart = 0;

  readonly etat = this.tracker.etat;
  readonly enCours = computed(() => this.etat() === 'enCours');
  readonly enPause = computed(() => this.etat() === 'enPause');
  readonly demarree = computed(() => this.etat() !== 'pret');
  readonly termine = computed(() => this.etat() === 'termine');

  readonly chrono = computed(() => formaterChrono(this.tracker.dureeS()));
  readonly distanceKm = computed(() => formaterDistance(this.tracker.distanceM()));
  readonly allureCourante = computed(() => formaterAllure(this.tracker.allureCouranteKmh()));
  readonly allureMoyenne = computed(() => formaterAllure(this.tracker.allureMoyenneKmh()));
  readonly splits = computed(() => this.tracker.splits());

  readonly cibleFormatee = computed(() => {
    const cible = this.cibleMinParKm();
    if (cible === null) return '—';
    return formaterAllure(minParKmVersKmh(cible));
  });

  readonly trace = computed<TraceSvg>(() =>
    projeterTrace(this.resume()?.points ?? this.tracker.points(), COTE_TRACE),
  );

  readonly ecartAllure = computed(() => {
    const cible = this.cibleMinParKm();
    const courante = kmhVersMinParKm(this.tracker.allureCouranteKmh());
    if (cible === null || courante === null || !this.enCours()) return 0;
    return courante - cible;
  });

  readonly tropLent = computed(() => this.ecartAllure() > ECART_TOLERE_MIN_PAR_KM);
  readonly tropRapide = computed(() => this.ecartAllure() < -ECART_TOLERE_MIN_PAR_KM);

  ngOnInit(): void {
    this.routineId = this.route.snapshot.paramMap.get('id') ?? '';
    this.exoId = this.route.snapshot.paramMap.get('exoId') ?? '';

    const exo = this.activeSession.exercices().find((e) => String(e.id) === this.exoId);
    if (!exo || exo.cardioType !== 'COURSE_EXTERIEUR') {
      this.router.navigate(['/session', this.routineId]);
      return;
    }

    this.exercice = exo;
    this.tracker.attacher(this.routineId, this.exoId);
    this.microDisponible.set(this.moteurReconnaissance() !== null);

    this.mappingCasque.set(this.lireMappingCasque());
    this.melangerMusique.set(this.lireMelangerMusique());

    const reprise = this.tracker.restaurer();
    const snapshot = this.tracker.lireSnapshot();
    const cible = snapshot?.cibleMinParKm ?? null;
    this.cibleMinParKm.set(cible);
    this.cibleSaisie.set(cible === null ? '' : formaterAllure(minParKmVersKmh(cible)));
    this.kmAnnonces = snapshot?.kmAnnonces ?? 0;

    if (reprise && this.enCours()) this.activerLeSon();
    this.ticker = setInterval(() => this.tick(), TICK_MS);
  }

  ngOnDestroy(): void {
    if (this.ticker) clearInterval(this.ticker);
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
    const franchis = Math.floor(this.tracker.distanceM() / KM_EN_METRES);
    if (franchis <= this.kmAnnonces) return;
    this.kmAnnonces = franchis;
    this.tracker.ecrireSnapshot({ kmAnnonces: franchis });
    this.dire(
      this.i18n.t('course.say.km', {
        km: franchis,
        temps: this.chronoParle(this.tracker.dureeS()),
        allure: this.allureParlee(this.tracker.allureMoyenneKmh()),
      }),
    );
  }

  // WHY: une annonce déclenchée au premier écart mesuré se répéterait à chaque tick sous un
  // pont ou dans une côte. L'écart doit tenir quinze secondes, et le coach se tait une minute
  // après avoir parlé — sinon l'athlète coupe le son et n'entend plus rien du tout.
  private surveillerAllure(): void {
    const derive = this.tropLent() || this.tropRapide();
    const maintenant = Date.now();

    if (!derive) {
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
    this.dire(this.i18n.t(this.consigneAllure()));
  }

  // WHY: un chiffre ne se corrige pas en courant. « Accélère un peu » se traduit tout de suite
  // en foulée, « 5:40 au kilomètre, vise 5:15 » demande un calcul que personne ne fait à
  // l'effort. Le chiffre reste disponible à la demande, par la commande « allure ».
  private consigneAllure(): string {
    const franc = Math.abs(this.ecartAllure()) >= ECART_FRANC_MIN_PAR_KM;
    if (this.tropLent()) return franc ? 'course.say.speedUp' : 'course.say.speedUpABit';
    return franc ? 'course.say.slowDown' : 'course.say.slowDownABit';
  }

  demarrer(): void {
    if (this.demarree()) return;
    this.activerLeSon();
    this.tracker.demarrer();
    this.tracker.ecrireSnapshot({ cibleMinParKm: this.cibleMinParKm(), kmAnnonces: 0 });
    this.dire(this.i18n.t('course.say.started'));
  }

  basculerPause(): void {
    if (!this.demarree() || this.termine()) return;
    // WHY: une page rechargée sur une course en pause n'a plus ni boucle de survie ni voix.
    // La reprise est un geste utilisateur : c'est la seule occasion de les rétablir.
    this.activerLeSon();
    this.tracker.basculerPause();
    this.telecommande?.annoncerEnCours(
      this.i18n.t('course.title'),
      this.enCours() ? 'playing' : 'paused',
    );
    this.dire(this.i18n.t(this.enCours() ? 'course.say.resumed' : 'course.say.paused'));
  }

  accelererCible(): void {
    this.deplacerCible(-PAS_CIBLE_MIN_PAR_KM);
  }

  ralentirCible(): void {
    this.deplacerCible(PAS_CIBLE_MIN_PAR_KM);
  }

  private deplacerCible(delta: number): void {
    this.fixerCible((this.cibleMinParKm() ?? CIBLE_DEFAUT_MIN_PAR_KM) + delta);
  }

  effacerCible(): void {
    this.cibleMinParKm.set(null);
    this.cibleSaisie.set('');
    this.tracker.ecrireSnapshot({ cibleMinParKm: null });
  }

  terminer(): void {
    if (this.termine() || this.enregistrement()) return;
    this.tracker.arreter();
    this.survie?.relacher();
    this.survie = null;
    this.arreterEcoute();
    this.dire(this.i18n.t('course.say.finished'), true);
    this.televerserTrace();
  }

  private televerserTrace(): void {
    const points = this.tracker.points();
    if (points.length < 2) {
      this.appliquerAuJournal(null);
      return;
    }

    this.enregistrement.set(true);
    this.erreurEnregistrement.set(false);
    this.chironApi.enregistrerTraceCourse(points).subscribe({
      next: (trace) => {
        this.enregistrement.set(false);
        this.resume.set(trace);
        this.appliquerAuJournal(trace);
      },
      error: () => {
        this.enregistrement.set(false);
        this.erreurEnregistrement.set(true);
        this.appliquerAuJournal(null);
      },
    });
  }

  reessayerEnregistrement(): void {
    if (this.enregistrement()) return;
    this.televerserTrace();
  }

  // WHY: la trace est téléversée seule, avant la séance. Seul son identifiant voyage ensuite
  // dans le payload d'enregistrement, qui est envoyé deux fois (séance jouée puis modèle) —
  // y faire circuler les points les écrirait en double sur le modèle de programme.
  private appliquerAuJournal(trace: CourseTraceDto | null): void {
    const serie = this.exercice?.series[0];
    if (!serie) return;

    const distanceM = trace?.distanceM ?? this.tracker.distanceM();
    const dureeS = trace?.dureeS ?? this.tracker.dureeS();

    serie.distanceM = Math.round(distanceM);
    serie.dureeMin = Math.round((dureeS / SECONDES_PAR_MINUTE) * 100) / 100;
    serie.allureKmh =
      Math.round((trace?.allureMoyenneKmh ?? this.tracker.allureMoyenneKmh()) * 100) / 100;
    serie.courseTraceId = trace?.id ?? null;
    this.activeSession.snapshot();
  }

  retourSeance(): void {
    this.tracker.purgerSnapshot();
    this.router.navigate(['/session', this.routineId]);
  }

  quitter(): void {
    if (this.demarree() && !this.termine() && !confirm(this.i18n.t('course.confirmLeave'))) return;
    if (this.demarree() && !this.termine()) {
      this.tracker.arreter();
      this.appliquerAuJournal(null);
    }
    this.retourSeance();
  }

  couleurSegment(allureKmh: number): string {
    const { allureMinKmh, allureMaxKmh } = this.trace();
    if (allureMaxKmh <= allureMinKmh) return 'hsl(200 90% 60%)';
    const ratio = Math.min(
      1,
      Math.max(0, (allureKmh - allureMinKmh) / (allureMaxKmh - allureMinKmh)),
    );
    return `hsl(${Math.round(220 - 220 * ratio)} 90% ${Math.round(52 + 12 * ratio)}%)`;
  }

  allureSplit(kmh: number): string {
    return formaterAllure(kmh);
  }

  dureeSplit(secondes: number): string {
    return formaterChrono(secondes);
  }

  // WHY: le contexte audio et la boucle de survie doivent naître dans le geste utilisateur.
  // Créés plus tard, Chrome les refuse : la page se ferait geler écran éteint et le coach
  // resterait muet pour toute la sortie.
  private activerLeSon(): void {
    if (this.survie) return;
    this.survie = tenirEnVie(['audioElement', 'webLock'], this.melangerMusique());
    this.audioActif.set(this.survie.audioEnLecture());
    this.voix = voixDisponible()
      ? creerVoix(this.i18n.lang() === 'en' ? 'en-US' : 'fr-FR', baisserLaMusiquePendant)
      : null;
    this.telecommande = brancherTelecommande(this.mappingCasque(), (action) =>
      this.executerActionCasque(action),
    );
    this.telecommande.annoncerEnCours(this.i18n.t('course.title'), 'playing');
  }

  private dire(texte: string, prioritaire = false): void {
    if (!this.voix) return;
    if (prioritaire) this.voix.interrompreEtParler(texte);
    else this.voix.parler(texte);
  }

  private chronoParle(secondes: number): string {
    const minutes = Math.floor(secondes / SECONDES_PAR_MINUTE);
    const reste = secondes % SECONDES_PAR_MINUTE;
    return this.i18n.t('course.say.duration', { minutes, secondes: reste });
  }

  private allureParlee(kmh: number): string {
    const minParKm = kmhVersMinParKm(kmh);
    if (minParKm === null) return this.i18n.t('course.say.noPace');
    const minutes = Math.floor(minParKm);
    const secondes = Math.round((minParKm - minutes) * SECONDES_PAR_MINUTE);
    return this.i18n.t('course.say.pace', { minutes, secondes });
  }

  private moteurReconnaissance(): any | null {
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
    moteur.lang = this.i18n.lang() === 'en' ? 'en-US' : 'fr-FR';
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
      this.dire(this.i18n.t('course.say.notUnderstood'), true);
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
        this.accelererCible();
        return;
      case 'moinsVite':
        this.ralentirCible();
        return;
      case 'cible':
        if (commande.cibleMinParKm !== undefined) this.fixerCible(commande.cibleMinParKm);
        return;
      case 'allure':
        this.dire(this.allureParlee(this.tracker.allureCouranteKmh()), true);
        return;
      case 'distance':
        this.dire(this.i18n.t('course.say.distance', { km: this.distanceKm() }), true);
        return;
      case 'duree':
        this.dire(this.chronoParle(this.tracker.dureeS()), true);
        return;
      case 'bilan':
        this.dire(
          this.i18n.t('course.say.summary', {
            km: this.distanceKm(),
            temps: this.chronoParle(this.tracker.dureeS()),
            allure: this.allureParlee(this.tracker.allureCouranteKmh()),
          }),
          true,
        );
        return;
    }
  }

  private executerActionCasque(action: ActionCasque): void {
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
    this.dire(this.i18n.t('course.say.listening'), true);
    this.commencerEcoute();
    if (this.minuterieEcoute) clearTimeout(this.minuterieEcoute);
    this.minuterieEcoute = setTimeout(() => this.terminerEcoute(), ECOUTE_MAINS_LIBRES_MS);
  }

  fixerCible(minParKm: number): void {
    const bornee = Math.min(CIBLE_MAX, Math.max(CIBLE_MIN, minParKm));
    this.cibleMinParKm.set(bornee);
    this.cibleSaisie.set(formaterAllure(minParKmVersKmh(bornee)));
    this.tracker.ecrireSnapshot({ cibleMinParKm: bornee });
    this.ecartDepuis = null;
    this.dire(
      this.i18n.t('course.say.target', { cible: this.allureParlee(minParKmVersKmh(bornee)) }),
      true,
    );
  }

  // WHY: l'athlète tape « 5:30 », « 530 » ou « 5.5 » selon le clavier que son téléphone lui
  // ouvre. Les trois désignent la même allure et doivent toutes être acceptées, sinon le champ
  // rejette une saisie manifestement juste.
  validerCibleSaisie(): void {
    const brut = this.cibleSaisie().trim();
    if (!brut) {
      this.effacerCible();
      return;
    }
    const cible = lireAllure(brut);
    if (cible === null) {
      const actuelle = this.cibleMinParKm();
      this.cibleSaisie.set(actuelle === null ? '' : formaterAllure(minParKmVersKmh(actuelle)));
      return;
    }
    this.fixerCible(cible);
  }

  // WHY: c'est un seul et même flux audio qui empêche le gel de la page et qui prend le focus
  // sonore. Mêler Chiron à la musique le rend interruptible, donc la page regelable : le
  // réglage est un arbitrage assumé, pas une préférence sans conséquence.
  basculerMelangerMusique(): void {
    const suivant = !this.melangerMusique();
    this.melangerMusique.set(suivant);
    try {
      localStorage.setItem(CLE_MELANGER_MUSIQUE, String(suivant));
    } catch {}
  }

  private lireMelangerMusique(): boolean {
    try {
      return localStorage.getItem(CLE_MELANGER_MUSIQUE) === 'true';
    } catch {
      return false;
    }
  }

  basculerReglages(): void {
    this.reglagesOuverts.update((ouvert) => !ouvert);
  }

  changerMappingCasque(bouton: BoutonCasque, action: ActionCasque): void {
    const suivant: MappingCasque = { ...this.mappingCasque(), [bouton]: action };
    this.mappingCasque.set(suivant);
    try {
      localStorage.setItem(CLE_MAPPING_CASQUE, JSON.stringify(suivant));
    } catch {}
    this.rebrancherTelecommande();
  }

  private lireMappingCasque(): MappingCasque {
    try {
      const brut = localStorage.getItem(CLE_MAPPING_CASQUE);
      if (!brut) return { ...MAPPING_PAR_DEFAUT };
      return { ...MAPPING_PAR_DEFAUT, ...(JSON.parse(brut) as Partial<MappingCasque>) };
    } catch {
      return { ...MAPPING_PAR_DEFAUT };
    }
  }

  private rebrancherTelecommande(): void {
    if (!this.telecommande) return;
    this.telecommande.relacher();
    this.telecommande = brancherTelecommande(this.mappingCasque(), (action) =>
      this.executerActionCasque(action),
    );
    this.telecommande.annoncerEnCours(
      this.i18n.t('course.title'),
      this.enCours() ? 'playing' : 'paused',
    );
  }
}
