import { Component, OnDestroy, OnInit, computed, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { ActiveSessionService } from '../../service/active-session.service';
import { I18nService } from '../../service/i18n.service';
import { TranslatePipe } from '../../service/translate.pipe';
import { HeaderComponent } from '../shared/header/header';
import { ExerciceForm } from '../../shared/exercise-forms';
import { WodSpec, wodSpec } from '../../shared/wod-specs';
import {
  VIBRATION_ALARME,
  playAnnonce,
  playEscargophone,
  unlockAudio,
  vibrer,
} from '../../util/escargophone';

const STORAGE_KEY = 'chiron.wod';
const TICK_MS = 250;
const DUREE_ALARME_MS = 15000;
const DERNIERE_LIGNE_DROITE_MS = 60000;
const FRAICHEUR_ANNONCE_MS = 5000;

interface WodSnapshot {
  routineId: string;
  exoId: string;
  startedAt: number;
  tours: number;
}

@Component({
  selector: 'app-wod',
  standalone: true,
  imports: [CommonModule, HeaderComponent, TranslatePipe],
  templateUrl: './wod.html',
  styleUrl: './wod.css',
})
export class Wod implements OnInit, OnDestroy {
  routineId = '';
  exoId = '';
  exercice: ExerciceForm | null = null;
  spec: WodSpec | null = null;

  readonly tours = signal(0);
  readonly record = signal<number | null>(null);
  readonly startedAt = signal<number | null>(null);
  readonly alarmeEnCours = signal(false);

  private readonly now = signal(Date.now());
  private ticker: ReturnType<typeof setInterval> | null = null;
  private arreterSonnerie: (() => void) | null = null;
  private finDeSonnerie: ReturnType<typeof setTimeout> | null = null;
  private wakeLock: any = null;
  private dureeMs = 0;
  private readonly annoncesPassees = new Set<number>();

  readonly restantMs = computed(() => {
    const debut = this.startedAt();
    if (debut === null) return this.dureeMs;
    return Math.max(0, this.dureeMs - (this.now() - debut));
  });

  readonly chrono = computed(() => {
    const secondes = Math.ceil(this.restantMs() / 1000);
    const mm = Math.floor(secondes / 60)
      .toString()
      .padStart(2, '0');
    const ss = (secondes % 60).toString().padStart(2, '0');
    return `${mm}:${ss}`;
  });

  readonly enCours = computed(() => this.startedAt() !== null && this.restantMs() > 0);
  readonly termine = computed(() => this.startedAt() !== null && this.restantMs() === 0);
  readonly derniereLigneDroite = computed(
    () => this.enCours() && this.restantMs() <= DERNIERE_LIGNE_DROITE_MS,
  );
  readonly recordBattu = computed(() => {
    const precedent = this.record();
    return precedent !== null && this.tours() > precedent;
  });

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private activeSession: ActiveSessionService,
    private i18n: I18nService,
  ) {}

  ngOnInit() {
    this.routineId = this.route.snapshot.paramMap.get('id') ?? '';
    this.exoId = this.route.snapshot.paramMap.get('exoId') ?? '';

    const exo = this.activeSession.exercices().find((e) => String(e.id) === this.exoId);
    this.spec = wodSpec(exo?.wodType);
    if (!exo || !this.spec) {
      this.router.navigate(['/programme']);
      return;
    }

    this.exercice = exo;
    this.dureeMs = this.spec.dureeMin * 60000;
    this.record.set(exo.series[0]?.reps ?? null);

    this.restaurerSnapshot();
    this.ticker = setInterval(() => this.tick(), TICK_MS);
  }

  ngOnDestroy() {
    if (this.ticker) clearInterval(this.ticker);
    this.couperAlarme();
    this.relacherWakeLock();
  }

  // WHY: un setInterval est bridé quand l'app passe en arrière-plan, et un setTimeout de vingt
  // minutes s'y déclenche très en retard. Le temps restant est donc recalculé depuis
  // l'horodatage de départ et la fin du WOD est constatée ici plutôt que programmée : revenir
  // sur l'écran affiche aussitôt la bonne valeur et déclenche l'alarme manquée.
  private tick() {
    this.now.set(Date.now());
    this.diffuserAnnonces();
    if (this.termine() && !this.alarmeEnCours() && this.arreterSonnerie === null) {
      this.sonner();
    }
  }

  // WHY: après un retour d'arrière-plan le temps a pu sauter de plusieurs minutes d'un tick au
  // suivant. Les paliers franchis pendant l'absence sont consommés en silence — seul un palier
  // franchi à l'instant est annoncé, sinon l'athlète entendrait « 15 minutes » alors qu'il en
  // reste 4.
  private diffuserAnnonces() {
    if (!this.enCours() || !this.spec) return;
    const restant = this.restantMs();

    for (const minutes of this.spec.annoncesMin) {
      const seuilMs = minutes * 60000;
      if (restant > seuilMs || this.annoncesPassees.has(minutes)) continue;
      this.annoncesPassees.add(minutes);
      if (seuilMs - restant <= FRAICHEUR_ANNONCE_MS) playAnnonce(minutes);
    }
  }

  demarrer() {
    if (this.startedAt() !== null) return;
    unlockAudio();
    this.demanderWakeLock();
    this.startedAt.set(Date.now());
    this.now.set(Date.now());
    vibrer(30);
    this.ecrireSnapshot();
  }

  ajouterTour() {
    if (!this.enCours()) return;
    this.tours.update((t) => t + 1);
    vibrer(20);
    this.ecrireSnapshot();
  }

  retirerTour() {
    if (this.startedAt() === null) return;
    this.tours.update((t) => Math.max(0, t - 1));
    this.ecrireSnapshot();
  }

  couperAlarme() {
    if (this.finDeSonnerie) {
      clearTimeout(this.finDeSonnerie);
      this.finDeSonnerie = null;
    }
    if (this.arreterSonnerie) {
      this.arreterSonnerie();
      this.arreterSonnerie = null;
    }
    this.alarmeEnCours.set(false);
  }

  private sonner() {
    this.alarmeEnCours.set(true);
    this.arreterSonnerie = playEscargophone();
    vibrer(VIBRATION_ALARME);
    this.relacherWakeLock();
    this.finDeSonnerie = setTimeout(() => this.couperAlarme(), DUREE_ALARME_MS);
  }

  retourSeance() {
    this.couperAlarme();
    if (this.exercice && this.startedAt() !== null) {
      const serie = this.exercice.series[0];
      if (serie) {
        serie.reps = this.tours();
        serie.poids = 0;
        serie.dureeMin = this.spec!.dureeMin;
      }
      this.activeSession.snapshot();
    }
    this.purgerSnapshot();
    this.router.navigate(['/session', this.routineId]);
  }

  quitter() {
    if (this.enCours() && !confirm(this.i18n.t('wod.confirmLeave'))) return;
    this.retourSeance();
  }

  private ecrireSnapshot() {
    const debut = this.startedAt();
    if (debut === null) return;
    const data: WodSnapshot = {
      routineId: this.routineId,
      exoId: this.exoId,
      startedAt: debut,
      tours: this.tours(),
    };
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(data));
    } catch {}
  }

  private restaurerSnapshot() {
    let brut: string | null = null;
    try {
      brut = localStorage.getItem(STORAGE_KEY);
    } catch {
      return;
    }
    if (!brut) return;

    try {
      const data = JSON.parse(brut) as WodSnapshot;
      const concerneCeWod =
        data && data.routineId === this.routineId && data.exoId === this.exoId && data.startedAt;
      if (concerneCeWod) {
        this.startedAt.set(data.startedAt);
        this.tours.set(data.tours ?? 0);
        this.now.set(Date.now());
      }
    } catch {}
  }

  private purgerSnapshot() {
    try {
      localStorage.removeItem(STORAGE_KEY);
    } catch {}
  }

  private demanderWakeLock() {
    const nav = navigator as any;
    if (!nav.wakeLock?.request) return;
    nav.wakeLock
      .request('screen')
      .then((lock: any) => (this.wakeLock = lock))
      .catch(() => {});
  }

  private relacherWakeLock() {
    if (!this.wakeLock) return;
    try {
      this.wakeLock.release();
    } catch {}
    this.wakeLock = null;
  }
}
