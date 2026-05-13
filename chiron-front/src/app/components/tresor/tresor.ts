import { Component, OnInit, signal, computed, HostListener } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, ActivatedRoute } from '@angular/router';
import { ChironApi } from '../../service/chiron-api';
import { AuthService } from '../../service/auth.service';

const TIERS = [
  { level: 1, name: 'Éphèbe',    cat: 'Novice'  },
  { level: 2, name: 'Argonaute', cat: 'Novice'  },
  { level: 3, name: 'Hoplite',   cat: 'Athlète' },
  { level: 4, name: 'Myrmidon',  cat: 'Athlète' },
  { level: 5, name: 'Spartiate', cat: 'Athlète' },
  { level: 6, name: 'Héros',     cat: 'Légende' },
  { level: 7, name: 'Demi-Dieu', cat: 'Légende' },
  { level: 8, name: 'Olympien',  cat: 'Légende' },
];

const THRESHOLDS: Record<string, number[]> = {
  DEVELOPPE_COUCHE: [0.70, 0.85, 1.00, 1.15, 1.30, 1.45, 1.60],
  SQUAT:            [0.95, 1.15, 1.30, 1.50, 1.70, 1.90, 2.10],
  SOULEVE_DE_TERRE: [1.10, 1.30, 1.50, 1.75, 2.00, 2.20, 2.40],
  TRACTIONS:        [1.05, 1.15, 1.25, 1.40, 1.55, 1.65, 1.75],
  DIPS:             [1.10, 1.20, 1.35, 1.50, 1.65, 1.80, 1.95],
};

const EXERCISE_META: Record<string, { subtitle: string; isBodyweight: boolean }> = {
  DEVELOPPE_COUCHE: { subtitle: 'Force des Bras',     isBodyweight: false },
  SQUAT:            { subtitle: 'Puissance des Jambes', isBodyweight: false },
  SOULEVE_DE_TERRE: { subtitle: 'Force de la Terre',  isBodyweight: false },
  TRACTIONS:        { subtitle: 'Maîtrise du Corps',  isBodyweight: true  },
  DIPS:             { subtitle: 'Puissance des Triceps', isBodyweight: true },
};

const WEIGHT_TICK_PX = 60;
const REP_TICK_PX    = 60;

@Component({
  selector: 'app-tresor',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './tresor.html',
  styleUrls: ['./tresor.css'],
})
export class Tresor implements OnInit {

  // ── Profile state ──────────────────────────────────────────────────────────
  viewedUsername = signal<string | null>(null);
  isMyProfile    = signal(true);
  myUsername     = signal<string | null>(null);
  isLoading      = signal(true);
  summary        = signal<any>(null);

  // ── Bodyweight edit ────────────────────────────────────────────────────────
  editingBodyweight   = signal(false);
  bodyweightDraft     = signal<number | null>(null);
  savingBodyweight    = signal(false);

  // ── Performance update modal ────────────────────────────────────────────────
  showModal        = signal(false);
  editingExercise  = signal<any>(null);
  isSaving         = signal(false);

  // Gear wheel values
  weightValue = signal(60);
  repsValue   = signal(5);

  // Sub-tick offsets for smooth drum animation
  weightOffset = signal(0);
  repsOffset   = signal(0);

  // Drag state
  private _dragging: 'weight' | 'reps' | null = null;
  private _startX    = 0;
  private _startWeight = 0;
  private _startReps   = 0;

  // ── Drum windows (9 visible items centered on current value) ──────────────
  readonly ITEM_W = 60; // px

  weightWindow = computed<(number | null)[]>(() => {
    const c = this.weightValue();
    return Array.from({ length: 9 }, (_, i) => {
      const v = Math.round((c + (i - 4) * 0.5) * 2) / 2;
      return v >= 0 && v <= 300 ? v : null;
    });
  });

  repsWindow = computed<(number | null)[]>(() => {
    const c = this.repsValue();
    return Array.from({ length: 9 }, (_, i) => {
      const v = c + (i - 4);
      return v >= 1 && v <= 36 ? v : null;
    });
  });

  // translateX to keep center item (index 4) at 50% of container
  // Strip moves LEFT when value increases (drag right): hence minus sign
  weightTransform = computed(() =>
    `translateX(calc(50% - ${4 * this.ITEM_W + this.ITEM_W / 2}px - ${this.weightOffset()}px))`
  );

  repsTransform = computed(() =>
    `translateX(calc(50% - ${4 * this.ITEM_W + this.ITEM_W / 2}px - ${this.repsOffset()}px))`
  );

  // Teeth shift opposite to drag (roll forward)
  weightTeethOffset = computed(() => `${((-this.weightOffset() % 20) + 20) % 20}px`);
  repsTeethOffset   = computed(() => `${((-this.repsOffset()   % 20) + 20) % 20}px`);

  // Live 1RM / tier preview
  preview1RM = computed(() => {
    const exercise = this.editingExercise();
    if (!exercise) return null;
    const w = this.weightValue();
    const r = this.repsValue();
    if (r < 1 || r > 36) return null;
    const meta = EXERCISE_META[exercise.exerciseType];
    const bw = this.summary()?.poidsCorps ?? null;
    const effectiveWeight = (meta?.isBodyweight && bw) ? w + bw : w;
    return Math.round(effectiveWeight * (36 / (37 - r)) * 100) / 100;
  });

  previewRatio = computed(() => {
    const rm1 = this.preview1RM();
    const bw = this.summary()?.poidsCorps;
    if (!rm1 || !bw) return null;
    return Math.round(rm1 / bw * 100) / 100;
  });

  previewTier = computed(() => {
    const ratio = this.previewRatio();
    const ex    = this.editingExercise();
    if (!ratio || !ex) return TIERS[0];
    const thresholds = THRESHOLDS[ex.exerciseType] ?? [];
    let tierIdx = 0;
    for (let i = 0; i < thresholds.length; i++) {
      if (ratio >= thresholds[i]) tierIdx = i + 1;
    }
    return TIERS[tierIdx];
  });

  constructor(
    private route:       ActivatedRoute,
    private router:      Router,
    public  chironApi:   ChironApi,
    private authService: AuthService,
  ) {}

  ngOnInit() {
    const me = this.authService.getUsername();
    this.myUsername.set(me);

    this.route.paramMap.subscribe(params => {
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
      next: data => {
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
      next: data => {
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
    // Default to last recorded values, or sensible defaults
    this.weightValue.set(exercise.poids ?? 60);
    this.repsValue.set(exercise.nombreReps ?? 5);
    this.weightOffset.set(0);
    this.repsOffset.set(0);
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

    this.isSaving.set(true);
    this.chironApi.addPerformanceRecord(username, {
      exerciseType: exercise.exerciseType,
      poids: this.weightValue(),
      nombreReps: this.repsValue(),
    }).subscribe({
      next: () => {
        this.isSaving.set(false);
        this.closeModal();
        this.loadSummary(username);
      },
      error: () => this.isSaving.set(false),
    });
  }

  // ── Gear drum drag ──────────────────────────────────────────────────────────

  startDrag(event: MouseEvent | TouchEvent, wheel: 'weight' | 'reps') {
    event.preventDefault();
    this._dragging     = wheel;
    this._startX       = this._clientX(event);
    this._startWeight  = this.weightValue();
    this._startReps    = this.repsValue();
    this.weightOffset.set(0);
    this.repsOffset.set(0);
  }

  @HostListener('document:mousemove', ['$event'])
  @HostListener('document:touchmove', ['$event'])
  onDragMove(event: MouseEvent | TouchEvent) {
    if (!this._dragging) return;

    const delta = this._clientX(event) - this._startX;

    if (this._dragging === 'weight') {
      const ticks     = Math.round(delta / WEIGHT_TICK_PX);
      const raw       = this._startWeight + ticks * 0.5;
      const snapped   = Math.round(Math.max(0, Math.min(300, raw)) * 2) / 2;
      this.weightValue.set(snapped);
      const wholePx   = ((snapped - this._startWeight) / 0.5) * WEIGHT_TICK_PX;
      this.weightOffset.set(delta - wholePx);
    } else {
      const ticks   = Math.round(delta / REP_TICK_PX);
      const snapped = Math.max(1, Math.min(36, this._startReps + ticks));
      this.repsValue.set(snapped);
      const wholePx = (snapped - this._startReps) * REP_TICK_PX;
      this.repsOffset.set(delta - wholePx);
    }
  }

  @HostListener('document:mouseup')
  @HostListener('document:touchend')
  onDragEnd() {
    if (!this._dragging) return;
    this._dragging = null;
    this.weightOffset.set(0);
    this.repsOffset.set(0);
  }

  // ── Helpers ─────────────────────────────────────────────────────────────────

  private _clientX(e: MouseEvent | TouchEvent): number {
    return 'touches' in e ? e.touches[0].clientX : e.clientX;
  }

  tierClass(level: number): string {
    return `tier-${Math.max(1, Math.min(8, level))}`;
  }

  getProgress(exercise: any): { percent: number; nextTier: string; remaining: number } | null {
    if (!exercise?.ratioPerformance || !exercise.exerciseType) return null;
    const level = exercise.tierLevel as number;
    if (level >= 8) return null;
    const thresholds = THRESHOLDS[exercise.exerciseType];
    if (!thresholds) return null;
    const next = thresholds[level - 1];
    const prev = level <= 1 ? 0 : thresholds[level - 2];
    const ratio = exercise.ratioPerformance;
    const percent = Math.min(100, Math.max(0, (ratio - prev) / (next - prev) * 100));
    return {
      percent: Math.round(percent),
      nextTier: TIERS[level].name,
      remaining: Math.max(0, Math.round((next - ratio) * 100) / 100),
    };
  }

  formatWeight(v: number | null): string {
    if (v === null) return '';
    return v % 1 === 0 ? String(v) : v.toFixed(1);
  }

  get tiers() { return TIERS; }

  isBodyweightExercise(exerciseType: string): boolean {
    return EXERCISE_META[exerciseType]?.isBodyweight ?? false;
  }

  exerciseSubtitle(exerciseType: string): string {
    return EXERCISE_META[exerciseType]?.subtitle ?? '';
  }

  goBack() {
    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.router.navigate(['/profile', id]);
    } else {
      this.router.navigate(['/profile']);
    }
  }
}
