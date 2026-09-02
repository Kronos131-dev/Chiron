import { Component, OnInit, signal, computed, HostListener } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, ActivatedRoute } from '@angular/router';
import { ChironApi } from '../../service/chiron-api';
import { AuthService } from '../../service/auth.service';
import { HeaderComponent } from '../shared/header/header';
import { TranslatePipe } from '../../service/translate.pipe';
import { tierBadgeUrl } from '../../shared/tier-badges';
import { TIERS, Tier } from '../../shared/tiers';

const THRESHOLDS: Record<string, number[]> = {
  DEVELOPPE_COUCHE: [0.7, 0.85, 1.0, 1.15, 1.3, 1.45, 1.6],
  SQUAT: [0.95, 1.15, 1.3, 1.5, 1.7, 1.9, 2.1],
  SOULEVE_DE_TERRE: [1.1, 1.3, 1.5, 1.75, 2.0, 2.2, 2.4],
  TRACTIONS: [1.05, 1.15, 1.25, 1.4, 1.55, 1.65, 1.75],
  DIPS: [1.1, 1.2, 1.35, 1.5, 1.65, 1.8, 1.95],
  COURSE_5KM: [8.0, 9.5, 11.0, 12.5, 14.0, 16.0, 18.0],
  COURSE_10KM: [7.5, 9.0, 10.5, 12.0, 13.5, 15.5, 17.5],
};

interface ExerciseMeta {
  subtitle: string;
  isBodyweight: boolean;
  distanceKm?: number;
  defaultTempsSecondes?: number;
}

const EXERCISE_META: Record<string, ExerciseMeta> = {
  DEVELOPPE_COUCHE: { subtitle: 'Force des Bras', isBodyweight: false },
  SQUAT: { subtitle: 'Puissance des Jambes', isBodyweight: false },
  SOULEVE_DE_TERRE: { subtitle: 'Force de la Terre', isBodyweight: false },
  TRACTIONS: { subtitle: 'Maîtrise du Corps', isBodyweight: true },
  DIPS: { subtitle: 'Puissance des Triceps', isBodyweight: true },
  COURSE_5KM: {
    subtitle: 'Souffle du Messager',
    isBodyweight: false,
    distanceKm: 5,
    defaultTempsSecondes: 1800,
  },
  COURSE_10KM: {
    subtitle: 'Endurance du Marathonien',
    isBodyweight: false,
    distanceKm: 10,
    defaultTempsSecondes: 3600,
  },
};

const ITEM_W = 60;
const TICK_PX = 60;
const VISIBLE_ITEMS = 9;
const CENTER_INDEX = 4;
const TEETH_PERIOD_PX = 20;

class Drum {
  readonly value = signal(0);
  readonly offset = signal(0);

  readonly window = computed<(number | null)[]>(() => {
    const center = this.value();
    return Array.from({ length: VISIBLE_ITEMS }, (_, i) => {
      const raw = center + (i - CENTER_INDEX) * this.step;
      const snapped = this.snap(raw);
      return snapped >= this.min && snapped <= this.max ? snapped : null;
    });
  });

  readonly transform = computed(
    () => `translateX(calc(50% - ${CENTER_INDEX * ITEM_W + ITEM_W / 2}px - ${this.offset()}px))`,
  );

  readonly teethOffset = computed(
    () => `${((-this.offset() % TEETH_PERIOD_PX) + TEETH_PERIOD_PX) % TEETH_PERIOD_PX}px`,
  );

  constructor(
    readonly min: number,
    readonly max: number,
    readonly step: number,
  ) {}

  set(raw: number) {
    this.value.set(this.clamp(raw));
    this.offset.set(0);
  }

  dragTo(startValue: number, deltaPx: number) {
    const ticks = Math.round(deltaPx / TICK_PX);
    const snapped = this.clamp(startValue + ticks * this.step);
    this.value.set(snapped);
    this.offset.set(deltaPx - ((snapped - startValue) / this.step) * TICK_PX);
  }

  rest() {
    this.offset.set(0);
  }

  private clamp(raw: number): number {
    return this.snap(Math.max(this.min, Math.min(this.max, raw)));
  }

  private snap(raw: number): number {
    return Math.round(raw / this.step) * this.step;
  }
}

@Component({
  selector: 'app-tresor',
  standalone: true,
  imports: [CommonModule, FormsModule, HeaderComponent, TranslatePipe],
  templateUrl: './tresor.html',
  styleUrls: ['./tresor.css'],
})
export class Tresor implements OnInit {
  // ── Profile state ──────────────────────────────────────────────────────────
  viewedUsername = signal<string | null>(null);
  isMyProfile = signal(true);
  myUsername = signal<string | null>(null);
  isLoading = signal(true);
  summary = signal<any>(null);

  // ── Bodyweight edit ────────────────────────────────────────────────────────
  editingBodyweight = signal(false);
  bodyweightDraft = signal<number | null>(null);
  savingBodyweight = signal(false);

  // ── Performance update modal ────────────────────────────────────────────────
  showModal = signal(false);
  editingExercise = signal<any>(null);
  isSaving = signal(false);

  // ── Tiers scale modal ──────────────────────────────────────────────────────
  showTiersModal = signal(false);
  readonly tierGroups: { cat: Tier['cat']; tiers: Tier[] }[] = (() => {
    const order: Tier['cat'][] = ['Novice', 'Athlète', 'Légende'];
    return order.map((cat) => ({ cat, tiers: TIERS.filter((t) => t.cat === cat) }));
  })();
  openTiersModal() {
    this.showTiersModal.set(true);
  }
  closeTiersModal() {
    this.showTiersModal.set(false);
  }

  readonly poidsDrum = new Drum(0, 300, 0.5);
  readonly repsDrum = new Drum(1, 36, 1);
  readonly minutesDrum = new Drum(0, 240, 1);
  readonly secondesDrum = new Drum(0, 59, 1);

  readonly ITEM_W = ITEM_W;

  private _dragging: Drum | null = null;
  private _startX = 0;
  private _startValue = 0;

  editedIsCourse = computed(() => this.isCourse(this.editingExercise()?.exerciseType));

  tempsSaisiSecondes = computed(() => this.minutesDrum.value() * 60 + this.secondesDrum.value());

  // Live 1RM / tier preview
  preview1RM = computed(() => {
    const exercise = this.editingExercise();
    if (!exercise || this.editedIsCourse()) return null;
    const w = this.poidsDrum.value();
    const r = this.repsDrum.value();
    if (r < 1 || r > 36) return null;
    const meta = EXERCISE_META[exercise.exerciseType];
    const bw = this.summary()?.poidsCorps ?? null;
    // Mirror backend cap: bodyweight exercise with no added weight → cap reps at 10
    const effectiveReps = meta?.isBodyweight && w === 0 ? Math.min(r, 10) : r;
    const effectiveWeight = meta?.isBodyweight && bw ? w + bw : w;
    return Math.round(effectiveWeight * (36 / (37 - effectiveReps)) * 100) / 100;
  });

  previewVitesse = computed(() => {
    const distance = this.distanceKm(this.editingExercise()?.exerciseType);
    const temps = this.tempsSaisiSecondes();
    if (!distance || temps <= 0) return null;
    return Math.round((distance / (temps / 3600)) * 100) / 100;
  });

  previewAllure = computed(() => {
    const distance = this.distanceKm(this.editingExercise()?.exerciseType);
    const temps = this.tempsSaisiSecondes();
    if (!distance || temps <= 0) return null;
    return this.formatChrono(Math.round(temps / distance));
  });

  previewRatio = computed(() => {
    if (this.editedIsCourse()) return this.previewVitesse();
    const rm1 = this.preview1RM();
    const bw = this.summary()?.poidsCorps;
    if (!rm1 || !bw) return null;
    return Math.round((rm1 / bw) * 100) / 100;
  });

  previewTier = computed(() => {
    const ratio = this.previewRatio();
    const ex = this.editingExercise();
    if (!ratio || !ex) return TIERS[0];
    return TIERS[this.tierIndexForRatio(ex.exerciseType, ratio)];
  });

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    public chironApi: ChironApi,
    private authService: AuthService,
  ) {}

  ngOnInit() {
    const me = this.authService.getUsername();
    this.myUsername.set(me);

    this.route.paramMap.subscribe((params) => {
      const id = params.get('id');
      const target = id ?? me ?? '';
      this.viewedUsername.set(target);
      this.isMyProfile.set(!id || id === me);
      this.loadSummary(target);
    });
  }

  loadSummary(username: string) {
    this.isLoading.set(true);
    this.chironApi.getPerformanceSummary(username).subscribe({
      next: (data) => {
        this.summary.set(data);
        this.isLoading.set(false);
      },
      error: () => {
        this.isLoading.set(false);
      },
    });
  }

  // ── Bodyweight ─────────────────────────────────────────────────────────────

  startEditBodyweight() {
    this.bodyweightDraft.set(this.summary()?.poidsCorps ?? null);
    this.editingBodyweight.set(true);
  }

  cancelEditBodyweight() {
    this.editingBodyweight.set(false);
  }

  saveBodyweight() {
    const val = this.bodyweightDraft();
    const username = this.viewedUsername();
    if (!val || val <= 0 || !username) return;

    this.savingBodyweight.set(true);
    this.chironApi.updateBodyweight(username, val).subscribe({
      next: (data) => {
        this.summary.set(data);
        this.editingBodyweight.set(false);
        this.savingBodyweight.set(false);
      },
      error: () => this.savingBodyweight.set(false),
    });
  }

  // ── Modal open/close ────────────────────────────────────────────────────────

  openModal(exercise: any) {
    this.editingExercise.set(exercise);

    if (this.isCourse(exercise.exerciseType)) {
      const meta = EXERCISE_META[exercise.exerciseType];
      const temps = exercise.tempsSecondes ?? meta?.defaultTempsSecondes ?? 1800;
      this.minutesDrum.set(Math.floor(temps / 60));
      this.secondesDrum.set(temps % 60);
    } else {
      const hasPrior = exercise.poids !== null && exercise.poids !== undefined;
      const isBodyweight = this.isBodyweightExercise(exercise.exerciseType);
      this.poidsDrum.set(hasPrior ? exercise.poids : isBodyweight ? 0 : 60);
      this.repsDrum.set(exercise.nombreReps ?? 5);
    }

    this.showModal.set(true);
  }

  closeModal() {
    this.showModal.set(false);
    this.editingExercise.set(null);
    this._dragging = null;
  }

  submitRecord() {
    const exercise = this.editingExercise();
    const username = this.viewedUsername();
    if (!exercise || !username) return;

    const record = this.editedIsCourse()
      ? { exerciseType: exercise.exerciseType, tempsSecondes: this.tempsSaisiSecondes() }
      : {
          exerciseType: exercise.exerciseType,
          poids: this.poidsDrum.value(),
          nombreReps: this.repsDrum.value(),
        };

    this.isSaving.set(true);
    this.chironApi.addPerformanceRecord(username, record).subscribe({
      next: (updatedSummary) => {
        this.summary.set(updatedSummary);
        this.isSaving.set(false);
        this.closeModal();
      },
      error: () => this.isSaving.set(false),
    });
  }

  // ── Gear drum drag ──────────────────────────────────────────────────────────

  startDrag(event: MouseEvent | TouchEvent, drum: Drum) {
    event.preventDefault();
    this._dragging = drum;
    this._startX = this._clientX(event);
    this._startValue = drum.value();
    drum.rest();
  }

  @HostListener('document:mousemove', ['$event'])
  @HostListener('document:touchmove', ['$event'])
  onDragMove(event: MouseEvent | TouchEvent) {
    if (!this._dragging) return;
    this._dragging.dragTo(this._startValue, this._clientX(event) - this._startX);
  }

  @HostListener('document:mouseup')
  @HostListener('document:touchend')
  onDragEnd() {
    if (!this._dragging) return;
    this._dragging.rest();
    this._dragging = null;
  }

  setFromInput(drum: Drum, val: number) {
    if (val === null || val === undefined || Number.isNaN(val)) return;
    drum.set(val);
  }

  // ── Helpers ─────────────────────────────────────────────────────────────────

  private _clientX(e: MouseEvent | TouchEvent): number {
    return 'touches' in e ? e.touches[0].clientX : e.clientX;
  }

  tierClass(level: number): string {
    return `tier-${Math.max(1, Math.min(8, level))}`;
  }

  /** Image du badge associée à un niveau de palier (1-8). */
  tierBadge(level: number | null | undefined): string {
    return tierBadgeUrl(level);
  }

  private tierIndexForRatio(exerciseType: string, ratio: number): number {
    const thresholds = THRESHOLDS[exerciseType] ?? [];
    let index = 0;
    for (let i = 0; i < thresholds.length; i++) {
      if (ratio >= thresholds[i]) index = i + 1;
    }
    return index;
  }

  getProgress(exercise: any): { percent: number; nextTier: string; manque: string } | null {
    if (!exercise?.ratioPerformance || !exercise.exerciseType) return null;
    const level = exercise.tierLevel as number;
    if (level >= 8) return null;
    const thresholds = THRESHOLDS[exercise.exerciseType];
    if (!thresholds) return null;
    const next = thresholds[level - 1];
    const prev = level <= 1 ? 0 : thresholds[level - 2];
    const ratio = exercise.ratioPerformance;
    const percent = Math.min(100, Math.max(0, ((ratio - prev) / (next - prev)) * 100));
    return {
      percent: Math.round(percent),
      nextTier: TIERS[level].name,
      manque: this.manqueVersSeuil(exercise, ratio, next),
    };
  }

  private manqueVersSeuil(exercise: any, ratio: number, seuil: number): string {
    const distance = this.distanceKm(exercise.exerciseType);
    if (!distance) {
      return `${Math.max(0, Math.round((seuil - ratio) * 100) / 100)}×`;
    }
    const secondesActuelles = Math.round((distance / ratio) * 3600);
    const secondesVisees = Math.round((distance / seuil) * 3600);
    return this.formatChrono(Math.max(0, secondesActuelles - secondesVisees));
  }

  formatWeight(v: number | null): string {
    if (v === null) return '';
    return v % 1 === 0 ? String(v) : v.toFixed(1);
  }

  formatChrono(secondes: number | null | undefined): string {
    if (secondes === null || secondes === undefined) return '';
    const minutes = Math.floor(secondes / 60);
    const reste = secondes % 60;
    return `${minutes}:${String(reste).padStart(2, '0')}`;
  }

  allureParKm(exercise: any): string {
    const distance = this.distanceKm(exercise?.exerciseType);
    if (!distance || !exercise?.tempsSecondes) return '';
    return this.formatChrono(Math.round(exercise.tempsSecondes / distance));
  }

  get tiers() {
    return TIERS;
  }

  isBodyweightExercise(exerciseType: string): boolean {
    return EXERCISE_META[exerciseType]?.isBodyweight ?? false;
  }

  isCourse(exerciseType: string | null | undefined): boolean {
    return !!exerciseType && EXERCISE_META[exerciseType]?.distanceKm !== undefined;
  }

  distanceKm(exerciseType: string | null | undefined): number | null {
    if (!exerciseType) return null;
    return EXERCISE_META[exerciseType]?.distanceKm ?? null;
  }

  exerciseSubtitle(exerciseType: string): string {
    return EXERCISE_META[exerciseType]?.subtitle ?? '';
  }

  backRoute = computed(() => {
    const id = this.route.snapshot.paramMap.get('id');
    return id ? `/profile/${id}` : '/profile';
  });

  goBack() {
    this.router.navigateByUrl(this.backRoute());
  }
}
