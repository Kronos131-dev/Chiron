import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { Capacitor } from '@capacitor/core';
import { retry } from 'rxjs';
import { ActiveSessionService } from '../../service/active-session.service';
import { AuthService } from '../../service/auth.service';
import { ChironApi, CoursePointDto, CourseTraceDto } from '../../service/chiron-api';
import { CLES_PHRASES, CourseRuntime, OptionsCourse } from '../../service/course-runtime';
import { RuntimeNatif } from '../../service/course-runtime-natif';
import { RuntimeWeb } from '../../service/course-runtime-web';
import { I18nService } from '../../service/i18n.service';
import { TranslatePipe } from '../../service/translate.pipe';
import { HeaderComponent } from '../shared/header/header';
import { ExerciceForm } from '../../shared/exercise-forms';
import {
  UNITES_ALLURE,
  UniteAllure,
  formaterAllure,
  formaterAllureSelon,
  formaterChrono,
  formaterDistance,
  kmhVersMinParKm,
  lireCible,
  minParKmVersKmh,
} from '../../util/allure';
import { TraceSvg, projeterTrace } from '../../util/trace-svg';
import { GrapheAllure, construireGrapheAllure } from '../../util/graphe-allure';
import { environment } from '../../../environments/environment';

const COTE_TRACE = 320;
const LARGEUR_GRAPHE = 320;
const HAUTEUR_GRAPHE = 120;
const SECONDES_PAR_MINUTE = 60;

const CIBLE_DEFAUT_MIN_PAR_KM = 6;
const CIBLE_MIN = 2.5;
const CIBLE_MAX = 15;
const PAS_CIBLE_MIN_PAR_KM = 5 / SECONDES_PAR_MINUTE;
const ECART_TOLERE_MIN_PAR_KM = 0.25;

const INTERVALLE_MIN_M = 100;
const INTERVALLE_MAX_M = 1000;
const INTERVALLE_PAS_M = 100;
const INTERVALLE_DEFAUT_M = 1000;

const VOLUME_MIN = 10;
const VOLUME_MAX = 100;
const VOLUME_DEFAUT = 100;

const CLE_MOT_CLE = 'chiron.course.motCle';
const CLE_UNITE_ALLURE = 'chiron.course.uniteAllure';
const CLE_VOLUME_VOIX = 'chiron.course.volumeVoix';
const CLE_OBJECTIF = 'chiron.course.objectifKm';
const CLE_INTERVALLE = 'chiron.course.intervalleAnnonce';
export const CLE_TRACE_EN_ATTENTE = 'chiron.course.traceEnAttente';
const OBJECTIF_MAX_KM = 300;
const TENTATIVES_TELEVERSEMENT = 3;
const DELAI_ENTRE_TENTATIVES_MS = 3000;

interface TraceEnAttente {
  routineId: string;
  exoId: string;
  debutSeance: string | null;
  objectifM: number | null;
  points: CoursePointDto[];
}

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
  private auth = inject(AuthService);

  readonly runtime: CourseRuntime = Capacitor.isNativePlatform()
    ? new RuntimeNatif()
    : new RuntimeWeb();

  routineId = '';
  exoId = '';
  exercice: ExerciceForm | null = null;

  readonly cibleMinParKm = signal<number | null>(null);
  readonly cibleSaisie = signal('');
  readonly reglagesOuverts = signal(false);
  readonly uniteAllure = signal<UniteAllure>('minParKm');
  readonly volumeVoix = signal(VOLUME_DEFAUT);
  readonly objectifKm = signal('');
  readonly motCle = signal(true);
  readonly intervalleAnnonce = signal(INTERVALLE_DEFAUT_M);
  readonly intervalleMin = INTERVALLE_MIN_M;
  readonly intervalleMax = INTERVALLE_MAX_M;
  readonly intervallePas = INTERVALLE_PAS_M;
  readonly graduations = [100, 250, 500, 750, 1000];
  readonly unites = UNITES_ALLURE;
  readonly volumeMin = VOLUME_MIN;
  readonly volumeMax = VOLUME_MAX;
  readonly enregistrement = signal(false);
  readonly erreurEnregistrement = signal(false);
  readonly resume = signal<CourseTraceDto | null>(null);

  readonly etat = this.runtime.etat;
  readonly ecoute = this.runtime.ecoute;
  readonly transcript = this.runtime.transcript;
  readonly commandeComprise = this.runtime.commandeComprise;
  readonly erreurMicro = this.runtime.erreurMicro;
  readonly audioActif = this.runtime.audioActif;
  readonly voixMuette = this.runtime.voixMuette;
  readonly objectifDureeS = this.runtime.objectifDureeS;
  readonly objectifAtteint = computed(() => this.objectifDureeS() > 0);
  readonly objectifResume = computed(() =>
    this.i18n.t('course.goalReached', {
      km: formaterDistance(this.objectifM()),
      temps: formaterChrono(this.objectifDureeS()),
    }),
  );
  readonly microDisponible = this.runtime.microDisponible;
  readonly motCleActif = this.runtime.motCleActif;
  readonly motCleIndisponible = this.runtime.motCleIndisponible;
  readonly natif = this.runtime.natif;

  readonly enCours = computed(() => this.etat() === 'enCours');
  readonly enPause = computed(() => this.etat() === 'enPause');
  readonly demarree = computed(() => this.etat() !== 'pret');
  readonly termine = computed(() => this.etat() === 'termine');

  // WHY: le journal ne garde que les mesures du serveur, recalculees sur les points recus. Le
  // chronometre, lui, court du bouton Demarrer au bouton Terminer — la minute passee a attendre
  // le premier point GPS comprise. Afficher les deux, c'est promettre a l'arrivee un chiffre que
  // le journal contredira ensuite : des que la trace est enregistree, l'ecran adopte le sien.
  private readonly mesuresConservees = computed(() => (this.termine() ? this.resume() : null));

  readonly chrono = computed(() =>
    formaterChrono(this.mesuresConservees()?.dureeS ?? this.runtime.dureeS()),
  );
  readonly distanceKm = computed(() =>
    formaterDistance(this.mesuresConservees()?.distanceM ?? this.runtime.distanceM()),
  );
  readonly allureCourante = computed(() => this.allureAffichee(this.runtime.allureCouranteKmh()));
  readonly allureMoyenne = computed(() =>
    this.allureAffichee(
      this.mesuresConservees()?.allureMoyenneKmh ?? this.runtime.allureMoyenneKmh(),
    ),
  );
  readonly splits = computed(() => this.runtime.splits());
  readonly precisionM = computed(() => this.runtime.precisionM());
  readonly erreurGps = computed(() => this.runtime.erreurGps());
  readonly signalPerdu = computed(() => this.runtime.signalPerdu());

  readonly intervalleLibelle = computed(() => this.libelleDistance(this.intervalleAnnonce()));

  readonly uniteLibelle = computed(() => this.i18n.t('course.paceUnitShort.' + this.uniteAllure()));
  readonly placeholderCible = computed(() =>
    this.i18n.t(
      this.uniteAllure() === 'kmh' ? 'course.targetPlaceholderKmh' : 'course.targetPlaceholder',
    ),
  );
  readonly messageMicro = computed(() => {
    const raison = this.erreurMicro();
    if (!raison) return '';
    const connue = raison === 'permission' || raison === 'moteur';
    return this.i18n.t('course.micError.' + (connue ? raison : 'autre'));
  });

  private readonly pointsTraces = computed(() => this.resume()?.points ?? this.runtime.points());

  readonly trace = computed<TraceSvg>(() => projeterTrace(this.pointsTraces(), COTE_TRACE));

  readonly graphe = computed<GrapheAllure | null>(() =>
    construireGrapheAllure(this.pointsTraces(), LARGEUR_GRAPHE, HAUTEUR_GRAPHE),
  );

  readonly ecartAllure = computed(() => {
    const cible = this.cibleMinParKm();
    const courante = kmhVersMinParKm(this.runtime.allureCouranteKmh());
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
    this.motCle.set(this.lire(CLE_MOT_CLE) !== 'non');
    this.intervalleAnnonce.set(this.lireIntervalle());
    this.uniteAllure.set(this.lireUnite());
    this.volumeVoix.set(this.lireVolume());
    this.objectifKm.set(this.lire(CLE_OBJECTIF) ?? '');
    this.runtime.attacher(this.routineId, this.exoId);
    this.runtime.configurer(this.options());
    this.reprendre();
  }

  private async reprendre(): Promise<void> {
    const reprise = await this.runtime.reprendreCourseEnCours();
    const cible = this.runtime.cibleRetenue();
    this.cibleMinParKm.set(cible);
    this.cibleSaisie.set(cible === null ? '' : this.saisiePourCible(cible));
    this.runtime.configurer(this.options());
    if (reprise) {
      await this.runtime.demarrer();
      return;
    }
    // WHY: la course a pu être close à la voix, écran verrouillé, sans que cette page existe.
    // Le service en a laissé l'archive ; c'est ici qu'elle devient une trace téléversée.
    if (this.termine()) {
      this.televerserTrace();
      return;
    }
    this.reprendreTraceEnAttente();
  }

  ngOnDestroy(): void {
    this.runtime.liberer();
  }

  private options(): OptionsCourse {
    return {
      langue: this.i18n.lang(),
      titre: this.i18n.t('course.title'),
      cibleMinParKm: this.cibleMinParKm(),
      phrases: this.construirePhrases(),
      uniteAllure: this.uniteAllure(),
      volumeVoix: this.volumeVoix(),
      objectifDistanceM: this.objectifM(),
      intervalleAnnonceM: this.intervalleAnnonce(),
      motCle: this.motCle(),
    };
  }

  // WHY: le service Android énonce et notifie sans repasser par le JS. Le libellé de l'unité
  // voyage donc avec les phrases, sinon la notification afficherait « /km » à un athlète qui a
  // choisi les km/h.
  private construirePhrases(): Record<string, string> {
    const phrases: Record<string, string> = {};
    for (const [court, cle] of Object.entries(CLES_PHRASES)) phrases[court] = this.i18n.t(cle);
    phrases['uniteAllure'] = this.uniteLibelle();
    return phrases;
  }

  // WHY: la distance visée est une intention, pas une limite. Elle déclenche une annonce et
  // fige le temps mis à l'atteindre ; la course, elle, ne s'arrête que quand l'athlète le
  // décide — il peut vouloir deux kilomètres de plus, et rien ne doit l'en empêcher.
  objectifM(): number {
    const km = Number.parseFloat(this.objectifKm().replace(',', '.'));
    if (!Number.isFinite(km) || km <= 0 || km > OBJECTIF_MAX_KM) return 0;
    return Math.round(km * 1000);
  }

  validerObjectif(): void {
    const metres = this.objectifM();
    if (metres <= 0) {
      this.objectifKm.set('');
      this.effacer(CLE_OBJECTIF);
    } else {
      this.ecrire(CLE_OBJECTIF, this.objectifKm());
    }
    this.runtime.configurer(this.options());
  }

  allureAffichee(kmh: number): string {
    return formaterAllureSelon(kmh, this.uniteAllure());
  }

  private saisiePourCible(minParKm: number): string {
    if (this.uniteAllure() === 'kmh') {
      return minParKmVersKmh(minParKm).toFixed(1).replace('.', ',');
    }
    return formaterAllure(minParKmVersKmh(minParKm));
  }

  demarrer(): void {
    if (this.demarree()) return;
    this.runtime.configurer(this.options());
    const token = this.auth.getToken();
    if (token) {
      const baseUrl = environment.apiUrl.replace(/\/api$/, '');
      this.runtime.configurerApiCommandes(baseUrl, token);
    }
    this.runtime.demarrer();
  }

  basculerPause(): void {
    if (!this.demarree() || this.termine()) return;
    this.runtime.basculerPause();
  }

  commencerEcoute(): void {
    this.runtime.commencerEcoute();
  }

  terminerEcoute(): void {
    this.runtime.terminerEcoute();
  }

  accelererCible(): void {
    this.fixerCible((this.cibleMinParKm() ?? CIBLE_DEFAUT_MIN_PAR_KM) - PAS_CIBLE_MIN_PAR_KM);
  }

  ralentirCible(): void {
    this.fixerCible((this.cibleMinParKm() ?? CIBLE_DEFAUT_MIN_PAR_KM) + PAS_CIBLE_MIN_PAR_KM);
  }

  effacerCible(): void {
    this.cibleMinParKm.set(null);
    this.cibleSaisie.set('');
    this.runtime.fixerCible(null);
  }

  fixerCible(minParKm: number): void {
    const bornee = Math.min(CIBLE_MAX, Math.max(CIBLE_MIN, minParKm));
    this.cibleMinParKm.set(bornee);
    this.cibleSaisie.set(this.saisiePourCible(bornee));
    this.runtime.fixerCible(bornee);
  }

  // WHY: l'athlète tape « 5:30 », « 530 » ou « 5.5 » selon le clavier que son téléphone lui
  // ouvre, et « 11,2 » quand il raisonne en km/h. Toutes ces saisies désignent une allure juste
  // et doivent être acceptées, sinon le champ rejette une valeur manifestement bonne.
  validerCibleSaisie(): void {
    const brut = this.cibleSaisie().trim();
    if (!brut) {
      this.effacerCible();
      return;
    }
    const cible = lireCible(brut, this.uniteAllure());
    if (cible === null) {
      const actuelle = this.cibleMinParKm();
      this.cibleSaisie.set(actuelle === null ? '' : this.saisiePourCible(actuelle));
      return;
    }
    this.fixerCible(cible);
  }

  async terminer(): Promise<void> {
    if (this.termine() || this.enregistrement()) return;
    await this.runtime.arreter();
    this.televerserTrace();
  }

  private televerserTrace(): void {
    const points = this.runtime.points();
    if (points.length < 2) {
      this.appliquerAuJournal(null);
      return;
    }
    const objectifM = this.objectifM() || null;
    this.retenirTraceEnAttente(points, objectifM);
    this.televerser(points, objectifM);
  }

  private televerser(points: CoursePointDto[], objectifM: number | null): void {
    this.enregistrement.set(true);
    this.erreurEnregistrement.set(false);
    this.chironApi
      .enregistrerTraceCourse(points, objectifM)
      .pipe(retry({ count: TENTATIVES_TELEVERSEMENT, delay: DELAI_ENTRE_TENTATIVES_MS }))
      .subscribe({
        next: (trace) => {
          this.enregistrement.set(false);
          this.oublierTraceEnAttente();
          if (this.termine()) this.resume.set(trace);
          this.appliquerAuJournal(trace);
        },
        error: () => {
          this.enregistrement.set(false);
          this.erreurEnregistrement.set(true);
          // WHY: la reprise d'une trace en attente se joue avant le départ, chronomètre à zéro.
          // Retomber ici sur les mesures du runtime effacerait la distance déjà inscrite dans
          // la séance ; il n'y a rien à réécrire tant que la sortie n'est pas celle de l'écran.
          if (this.termine()) this.appliquerAuJournal(null);
        },
      });
  }

  reessayerEnregistrement(): void {
    if (this.enregistrement()) return;
    this.televerserTrace();
  }

  // WHY: l'athlète termine sa sortie loin de tout réseau. Une fois le téléversement échoué et
  // l'écran quitté, les points n'existaient plus nulle part : le service natif est arrêté, le
  // journal gardait une course sans parcours et rien ne permettait plus de la retrouver.
  private retenirTraceEnAttente(points: CoursePointDto[], objectifM: number | null): void {
    const attente: TraceEnAttente = {
      routineId: this.routineId,
      exoId: this.exoId,
      debutSeance: this.activeSession.startedAt(),
      objectifM,
      points,
    };
    this.ecrire(CLE_TRACE_EN_ATTENTE, JSON.stringify(attente));
  }

  private oublierTraceEnAttente(): void {
    this.effacer(CLE_TRACE_EN_ATTENTE);
  }

  // WHY: la trace en attente n'appartient qu'à la séance qui l'a produite. Le début de séance
  // la distingue d'une sortie faite hier sur la même routine, qu'il ne faut surtout pas
  // rattacher à la course du jour.
  private reprendreTraceEnAttente(): void {
    const brut = this.lire(CLE_TRACE_EN_ATTENTE);
    if (!brut) return;

    let attente: TraceEnAttente;
    try {
      attente = JSON.parse(brut) as TraceEnAttente;
    } catch {
      this.oublierTraceEnAttente();
      return;
    }

    if (attente?.routineId !== this.routineId || attente?.exoId !== this.exoId) return;
    if (attente.debutSeance !== this.activeSession.startedAt()) {
      this.oublierTraceEnAttente();
      return;
    }
    if (!Array.isArray(attente.points) || attente.points.length < 2) {
      this.oublierTraceEnAttente();
      return;
    }
    this.televerser(attente.points, attente.objectifM ?? null);
  }

  // WHY: la trace est téléversée seule, avant la séance. Seul son identifiant voyage ensuite
  // dans le payload d'enregistrement, qui est envoyé deux fois (séance jouée puis modèle) —
  // y faire circuler les points les écrirait en double sur le modèle de programme.
  private appliquerAuJournal(trace: CourseTraceDto | null): void {
    const serie = this.exercice?.series[0];
    if (!serie) return;

    const distanceM = trace?.distanceM ?? this.runtime.distanceM();
    const dureeS = trace?.dureeS ?? this.runtime.dureeS();

    serie.distanceM = Math.round(distanceM);
    serie.dureeMin = Math.round((dureeS / SECONDES_PAR_MINUTE) * 100) / 100;
    serie.allureKmh =
      Math.round((trace?.allureMoyenneKmh ?? this.runtime.allureMoyenneKmh()) * 100) / 100;
    serie.courseTraceId = trace?.id ?? null;
    this.activeSession.snapshot();
  }

  retourSeance(): void {
    this.runtime.purger();
    this.router.navigate(['/session', this.routineId]);
  }

  async quitter(): Promise<void> {
    if (this.demarree() && !this.termine() && !confirm(this.i18n.t('course.confirmLeave'))) return;
    if (this.demarree() && !this.termine()) {
      await this.runtime.arreter();
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
    return this.allureAffichee(kmh);
  }

  dureeSplit(secondes: number): string {
    return formaterChrono(secondes);
  }

  choisirUnite(unite: UniteAllure): void {
    if (this.uniteAllure() === unite) return;
    this.uniteAllure.set(unite);
    this.ecrire(CLE_UNITE_ALLURE, unite);
    const cible = this.cibleMinParKm();
    this.cibleSaisie.set(cible === null ? '' : this.saisiePourCible(cible));
    this.runtime.configurer(this.options());
  }

  changerVolume(valeur: string): void {
    const brut = Number.parseInt(valeur, 10);
    if (!Number.isFinite(brut)) return;
    this.volumeVoix.set(Math.min(VOLUME_MAX, Math.max(VOLUME_MIN, brut)));
  }

  // WHY: régler un volume sans l'entendre revient à le régler au hasard. La voix parle donc à
  // chaque relâchement du curseur, au niveau exact qui vient d'être choisi.
  essayerLaVoix(): void {
    this.ecrire(CLE_VOLUME_VOIX, String(this.volumeVoix()));
    this.runtime.configurer(this.options());
    this.runtime.essayerVoix(this.i18n.t('course.say.volumeTest'));
  }

  basculerReglages(): void {
    this.reglagesOuverts.update((ouvert) => !ouvert);
  }

  // WHY: la droite graduée envoie une chaîne. Elle est bornée et alignée sur le pas ici, à la
  // frontière du clavier, parce que rien en aval ne saura distinguer un réglage d'un accident.
  changerIntervalle(valeur: string): void {
    const brut = Number.parseInt(valeur, 10);
    if (!Number.isFinite(brut)) return;
    const borne = Math.min(INTERVALLE_MAX_M, Math.max(INTERVALLE_MIN_M, brut));
    this.intervalleAnnonce.set(Math.round(borne / INTERVALLE_PAS_M) * INTERVALLE_PAS_M);
  }

  validerIntervalle(): void {
    this.ecrire(CLE_INTERVALLE, String(this.intervalleAnnonce()));
    this.runtime.configurer(this.options());
  }

  positionGraduation(metres: number): number {
    return ((metres - INTERVALLE_MIN_M) / (INTERVALLE_MAX_M - INTERVALLE_MIN_M)) * 100;
  }

  libelleDistance(metres: number): string {
    if (metres < 1000) return `${metres} m`;
    return `${metres / 1000} km`;
  }

  private lireIntervalle(): number {
    const brut = Number.parseInt(this.lire(CLE_INTERVALLE) ?? '', 10);
    if (!Number.isFinite(brut)) return INTERVALLE_DEFAUT_M;
    return Math.min(INTERVALLE_MAX_M, Math.max(INTERVALLE_MIN_M, brut));
  }

  basculerMotCle(): void {
    this.motCle.update((actif) => !actif);
    this.ecrire(CLE_MOT_CLE, this.motCle() ? 'oui' : 'non');
    this.runtime.configurer(this.options());
  }

  private lireUnite(): UniteAllure {
    return this.lire(CLE_UNITE_ALLURE) === 'kmh' ? 'kmh' : 'minParKm';
  }

  private lireVolume(): number {
    const brut = Number.parseInt(this.lire(CLE_VOLUME_VOIX) ?? '', 10);
    if (!Number.isFinite(brut)) return VOLUME_DEFAUT;
    return Math.min(VOLUME_MAX, Math.max(VOLUME_MIN, brut));
  }

  private lire(cle: string): string | null {
    try {
      return localStorage.getItem(cle);
    } catch {
      return null;
    }
  }

  private effacer(cle: string): void {
    try {
      localStorage.removeItem(cle);
    } catch {}
  }

  private ecrire(cle: string, valeur: string): void {
    try {
      localStorage.setItem(cle, valeur);
    } catch {}
  }
}
