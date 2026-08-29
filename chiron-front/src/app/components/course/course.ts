import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { Capacitor } from '@capacitor/core';
import { ActiveSessionService } from '../../service/active-session.service';
import { ChironApi, CourseTraceDto } from '../../service/chiron-api';
import { CLES_PHRASES, CourseRuntime } from '../../service/course-runtime';
import { RuntimeNatif } from '../../service/course-runtime-natif';
import { RuntimeWeb } from '../../service/course-runtime-web';
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
import {
  ACTIONS_CASQUE,
  ActionCasque,
  BOUTONS_CASQUE,
  BoutonCasque,
  MAPPING_LONG_PAR_DEFAUT,
  MAPPING_PAR_DEFAUT,
  MappingCasque,
} from '../../util/telecommande-casque';
import { lireAllure } from '../../util/commandes-vocales';
import { TraceSvg, projeterTrace } from '../../util/trace-svg';

const COTE_TRACE = 320;
const SECONDES_PAR_MINUTE = 60;

const CIBLE_DEFAUT_MIN_PAR_KM = 6;
const CIBLE_MIN = 2.5;
const CIBLE_MAX = 15;
const PAS_CIBLE_MIN_PAR_KM = 5 / SECONDES_PAR_MINUTE;
const ECART_TOLERE_MIN_PAR_KM = 0.25;

const CLE_MAPPING_CASQUE = 'chiron.course.casque';
const CLE_MAPPING_CASQUE_LONG = 'chiron.course.casqueLong';
const CLE_MELANGER_MUSIQUE = 'chiron.course.melangerMusique';

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

  readonly runtime: CourseRuntime = Capacitor.isNativePlatform()
    ? new RuntimeNatif()
    : new RuntimeWeb();

  routineId = '';
  exoId = '';
  exercice: ExerciceForm | null = null;

  readonly cibleMinParKm = signal<number | null>(null);
  readonly cibleSaisie = signal('');
  readonly reglagesOuverts = signal(false);
  readonly melangerMusique = signal(false);
  readonly mappingCasque = signal<MappingCasque>({ ...MAPPING_PAR_DEFAUT });
  readonly mappingCasqueLong = signal<MappingCasque>({ ...MAPPING_LONG_PAR_DEFAUT });
  readonly boutonsCasque = BOUTONS_CASQUE;
  readonly actionsCasque = ACTIONS_CASQUE;
  readonly enregistrement = signal(false);
  readonly erreurEnregistrement = signal(false);
  readonly resume = signal<CourseTraceDto | null>(null);

  readonly etat = this.runtime.etat;
  readonly ecoute = this.runtime.ecoute;
  readonly transcript = this.runtime.transcript;
  readonly commandeComprise = this.runtime.commandeComprise;
  readonly audioActif = this.runtime.audioActif;
  readonly microDisponible = this.runtime.microDisponible;
  readonly natif = this.runtime.natif;

  readonly enCours = computed(() => this.etat() === 'enCours');
  readonly enPause = computed(() => this.etat() === 'enPause');
  readonly demarree = computed(() => this.etat() !== 'pret');
  readonly termine = computed(() => this.etat() === 'termine');

  readonly chrono = computed(() => formaterChrono(this.runtime.dureeS()));
  readonly distanceKm = computed(() => formaterDistance(this.runtime.distanceM()));
  readonly allureCourante = computed(() => formaterAllure(this.runtime.allureCouranteKmh()));
  readonly allureMoyenne = computed(() => formaterAllure(this.runtime.allureMoyenneKmh()));
  readonly splits = computed(() => this.runtime.splits());
  readonly precisionM = computed(() => this.runtime.precisionM());
  readonly erreurGps = computed(() => this.runtime.erreurGps());
  readonly signalPerdu = computed(() => this.runtime.signalPerdu());

  readonly trace = computed<TraceSvg>(() =>
    projeterTrace(this.resume()?.points ?? this.runtime.points(), COTE_TRACE),
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
    this.mappingCasque.set(this.lireMapping(CLE_MAPPING_CASQUE, MAPPING_PAR_DEFAUT));
    this.mappingCasqueLong.set(this.lireMapping(CLE_MAPPING_CASQUE_LONG, MAPPING_LONG_PAR_DEFAUT));
    this.melangerMusique.set(this.lireMelangerMusique());
    this.runtime.attacher(this.routineId, this.exoId);
    this.runtime.configurer(this.options());
    this.reprendre();
  }

  private async reprendre(): Promise<void> {
    const reprise = await this.runtime.reprendreCourseEnCours();
    const cible = this.runtime.cibleRetenue();
    this.cibleMinParKm.set(cible);
    this.cibleSaisie.set(cible === null ? '' : formaterAllure(minParKmVersKmh(cible)));
    this.runtime.configurer(this.options());
    if (reprise) await this.runtime.demarrer();
  }

  ngOnDestroy(): void {
    this.runtime.liberer();
  }

  private options() {
    return {
      langue: this.i18n.lang(),
      titre: this.i18n.t('course.title'),
      cibleMinParKm: this.cibleMinParKm(),
      phrases: this.construirePhrases(),
      appuiCourt: this.mappingCasque(),
      appuiLong: this.mappingCasqueLong(),
      melangerMusique: this.melangerMusique(),
    };
  }

  private construirePhrases(): Record<string, string> {
    const phrases: Record<string, string> = {};
    for (const [court, cle] of Object.entries(CLES_PHRASES)) phrases[court] = this.i18n.t(cle);
    return phrases;
  }

  demarrer(): void {
    if (this.demarree()) return;
    this.runtime.configurer(this.options());
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
    this.cibleSaisie.set(formaterAllure(minParKmVersKmh(bornee)));
    this.runtime.fixerCible(bornee);
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
    return formaterAllure(kmh);
  }

  dureeSplit(secondes: number): string {
    return formaterChrono(secondes);
  }

  // WHY: c'est un seul et même flux audio qui empêche le gel de la page et qui prend le focus
  // sonore. Mêler Chiron à la musique le rend interruptible, donc la page regelable : le
  // réglage est un arbitrage assumé, pas une préférence sans conséquence. Le service Android
  // n'a pas ce dilemme, et le réglage n'y est pas montré.
  basculerMelangerMusique(): void {
    const suivant = !this.melangerMusique();
    this.melangerMusique.set(suivant);
    this.runtime.configurer(this.options());
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
    this.mappingCasque.set({ ...this.mappingCasque(), [bouton]: action });
    this.ecrireMapping(CLE_MAPPING_CASQUE, this.mappingCasque());
    this.runtime.configurer(this.options());
  }

  changerMappingCasqueLong(bouton: BoutonCasque, action: ActionCasque): void {
    this.mappingCasqueLong.set({ ...this.mappingCasqueLong(), [bouton]: action });
    this.ecrireMapping(CLE_MAPPING_CASQUE_LONG, this.mappingCasqueLong());
    this.runtime.configurer(this.options());
  }

  private lireMapping(cle: string, defaut: MappingCasque): MappingCasque {
    try {
      const brut = localStorage.getItem(cle);
      if (!brut) return { ...defaut };
      return { ...defaut, ...(JSON.parse(brut) as Partial<MappingCasque>) };
    } catch {
      return { ...defaut };
    }
  }

  private ecrireMapping(cle: string, mapping: MappingCasque): void {
    try {
      localStorage.setItem(cle, JSON.stringify(mapping));
    } catch {}
  }
}
