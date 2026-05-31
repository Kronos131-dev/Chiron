import { Component, OnInit, signal, computed, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { BaseChartDirective } from 'ng2-charts';
import { ChartConfiguration, ChartData } from 'chart.js';
import { HeaderComponent } from '../shared/header/header';
import { AuthService } from '../../service/auth.service';
import { formatDuration } from '../../util/duration';
import {
  ChironApi,
  StatsOverview,
  WeeklyVolumePoint,
  MuscleStats,
  ExerciseListItem,
  ExerciseProgressPoint,
  NutritionStats,
  BodyweightStats,
  RecoveryPoint,
  PerformanceSummary,
  PerformanceExercise,
} from '../../service/chiron-api';

type Tab = 'overview' | 'force' | 'exercices' | 'volume' | 'corps' | 'nutrition' | 'recup';
type Periode = 30 | 90 | 365 | 3650;
type ProgressMetric = 'e1rm' | 'chargeMax' | 'volume';

/**
 * Page Statistiques : regroupe tous les graphes de l'utilisateur (force, volume,
 * progression par exercice, poids de corps, nutrition, récupération). Mobile-first :
 * navigation par onglets, une section visible à la fois, chargement paresseux par onglet.
 */
@Component({
  selector: 'app-statistics',
  standalone: true,
  imports: [CommonModule, FormsModule, HeaderComponent, BaseChartDirective],
  templateUrl: './statistics.html',
  styleUrls: ['./statistics.css'],
})
export class Statistics implements OnInit {
  private api = inject(ChironApi);

  /** Exposé au template pour formater les durées (« 72 min » / « — »). */
  protected formatDuration = formatDuration;
  private auth = inject(AuthService);
  private router = inject(Router);

  private username = this.auth.getUsername() ?? '';

  // Palette alignée sur le thème.
  private readonly COL = {
    orange: '#ffb779',
    cyan: '#22d3ee',
    indigo: '#818cf8',
    green: '#34d399',
    red: '#f87171',
    violet: '#c084fc',
    amber: '#fbbf24',
    grid: 'rgba(255,255,255,0.06)',
    text: '#94a3b8',
  };

  readonly tabs: { id: Tab; label: string; icon: string }[] = [
    { id: 'overview', label: "Vue d'ensemble", icon: 'dashboard' },
    { id: 'force', label: 'Force', icon: 'fitness_center' },
    { id: 'exercices', label: 'Exercices', icon: 'show_chart' },
    { id: 'volume', label: 'Volume', icon: 'bar_chart' },
    { id: 'corps', label: 'Corps', icon: 'monitor_weight' },
    { id: 'nutrition', label: 'Nutrition', icon: 'restaurant' },
    { id: 'recup', label: 'Récup', icon: 'bedtime' },
  ];

  readonly periodes: { value: Periode; label: string }[] = [
    { value: 30, label: '30 j' },
    { value: 90, label: '90 j' },
    { value: 365, label: '1 an' },
    { value: 3650, label: 'Tout' },
  ];

  activeTab = signal<Tab>('overview');
  periode = signal<Periode>(90);

  // Données par onglet
  overview = signal<StatsOverview | null>(null);

  perf = signal<PerformanceSummary | null>(null);
  selectedLift = signal<string | null>(null);
  liftHistory = signal<PerformanceExercise[]>([]);

  exerciseList = signal<ExerciseListItem[]>([]);
  exerciseQuery = signal<string>('');
  selectedExercise = signal<string | null>(null);
  exerciseProgress = signal<ExerciseProgressPoint[]>([]);
  progressMetric = signal<ProgressMetric>('e1rm');

  volume = signal<WeeklyVolumePoint[]>([]);
  muscles = signal<MuscleStats | null>(null);

  bodyweight = signal<BodyweightStats | null>(null);
  nutrition = signal<NutritionStats | null>(null);
  recovery = signal<RecoveryPoint[]>([]);

  // États de chargement / erreur, par onglet
  loading = signal<Record<Tab, boolean>>({
    overview: false, force: false, exercices: false, volume: false, corps: false, nutrition: false, recup: false,
  });
  private loaded = new Set<Tab>();

  ngOnInit(): void {
    this.selectTab('overview');
  }

  // ----------------------------------------------------------- Navigation

  selectTab(tab: Tab): void {
    this.activeTab.set(tab);
    if (!this.loaded.has(tab)) {
      this.loadTab(tab);
    }
  }

  changePeriode(p: Periode): void {
    if (p === this.periode()) return;
    this.periode.set(p);
    // Les onglets dépendant de la période doivent être rechargés.
    const dependents: Tab[] = ['volume', 'corps', 'nutrition', 'recup'];
    for (const t of dependents) this.loaded.delete(t);
    this.loadTab(this.activeTab());
  }

  private days(): number {
    return this.periode();
  }

  private setLoading(tab: Tab, value: boolean): void {
    this.loading.update((s) => ({ ...s, [tab]: value }));
  }

  private loadTab(tab: Tab): void {
    this.setLoading(tab, true);
    switch (tab) {
      case 'overview':
        this.api.getStatsOverview().subscribe({
          next: (d) => { this.overview.set(d); this.done(tab); },
          error: () => this.done(tab),
        });
        break;
      case 'force':
        this.api.getPerformanceSummary(this.username).subscribe({
          next: (d) => {
            const summary = d as PerformanceSummary;
            this.perf.set(summary);
            const withRecord = summary.exercises.find((e) => e.recordedAt);
            if (withRecord && !this.selectedLift()) this.selectLift(withRecord.exerciseType);
            this.done(tab);
          },
          error: () => this.done(tab),
        });
        break;
      case 'exercices':
        this.api.getStatsExercises().subscribe({
          next: (d) => {
            this.exerciseList.set(d);
            if (d.length && !this.selectedExercise()) this.selectExercise(d[0].nom);
            this.done(tab);
          },
          error: () => this.done(tab),
        });
        break;
      case 'volume': {
        const weeks = Math.min(Math.max(Math.ceil(this.days() / 7), 4), 52);
        let pending = 2;
        const fin = () => { if (--pending === 0) this.done(tab); };
        this.api.getStatsVolume(weeks).subscribe({ next: (d) => { this.volume.set(d); fin(); }, error: fin });
        this.api.getStatsMuscles(this.days()).subscribe({ next: (d) => { this.muscles.set(d); fin(); }, error: fin });
        break;
      }
      case 'corps':
        this.api.getStatsBodyweight(this.days()).subscribe({
          next: (d) => { this.bodyweight.set(d); this.done(tab); },
          error: () => this.done(tab),
        });
        break;
      case 'nutrition':
        this.api.getStatsNutrition(Math.min(this.days(), 365)).subscribe({
          next: (d) => { this.nutrition.set(d); this.done(tab); },
          error: () => this.done(tab),
        });
        break;
      case 'recup':
        this.api.getRecovery(Math.min(this.days(), 90)).subscribe({
          next: (d) => { this.recovery.set(d); this.done(tab); },
          error: () => this.done(tab),
        });
        break;
    }
  }

  private done(tab: Tab): void {
    this.loaded.add(tab);
    this.setLoading(tab, false);
  }

  // ----------------------------------------------------------- Force

  selectLift(type: string): void {
    this.selectedLift.set(type);
    this.api.getPerformanceHistory(this.username, type).subscribe({
      next: (d) => this.liftHistory.set((d as PerformanceExercise[]).slice().reverse()),
      error: () => this.liftHistory.set([]),
    });
  }

  liftChart = computed<ChartData<'line'>>(() => {
    const h = this.liftHistory();
    return {
      labels: h.map((p) => this.dateLabel(p.recordedAt)),
      datasets: [{
        data: h.map((p) => p.rm1Estime ?? 0),
        label: '1RM estimé (kg)',
        borderColor: this.COL.orange,
        backgroundColor: 'rgba(255,183,121,0.15)',
        fill: true,
        tension: 0.3,
        pointRadius: 3,
        pointBackgroundColor: this.COL.orange,
      }],
    };
  });

  // ----------------------------------------------------------- Exercices

  filteredExercises = computed<ExerciseListItem[]>(() => {
    const q = this.exerciseQuery().trim().toLowerCase();
    const list = this.exerciseList();
    if (!q) return list.slice(0, 30);
    return list.filter((e) => e.nom.toLowerCase().includes(q)).slice(0, 30);
  });

  selectExercise(nom: string): void {
    this.selectedExercise.set(nom);
    this.api.getStatsExerciseProgress(nom).subscribe({
      next: (d) => this.exerciseProgress.set(d),
      error: () => this.exerciseProgress.set([]),
    });
  }

  setMetric(m: ProgressMetric): void {
    this.progressMetric.set(m);
  }

  exerciseChart = computed<ChartData<'line'>>(() => {
    const pts = this.exerciseProgress();
    const metric = this.progressMetric();
    const label = metric === 'e1rm' ? '1RM estimé (kg)' : metric === 'chargeMax' ? 'Charge max (kg)' : 'Volume (kg)';
    const color = metric === 'e1rm' ? this.COL.orange : metric === 'chargeMax' ? this.COL.cyan : this.COL.indigo;
    return {
      labels: pts.map((p) => this.dateLabel(p.date)),
      datasets: [{
        data: pts.map((p) => p[metric]),
        label,
        borderColor: color,
        backgroundColor: this.hexA(color, 0.15),
        fill: true,
        tension: 0.3,
        pointRadius: 2,
      }],
    };
  });

  // ----------------------------------------------------------- Volume / muscles

  volumeChart = computed<ChartData<'bar'>>(() => {
    const v = this.volume();
    return {
      labels: v.map((p) => p.label),
      datasets: [{
        data: v.map((p) => p.tonnage),
        label: 'Tonnage (kg)',
        backgroundColor: this.hexA(this.COL.orange, 0.55),
        borderRadius: 4,
      }],
    };
  });

  sessionsChart = computed<ChartData<'bar'>>(() => {
    const v = this.volume();
    return {
      labels: v.map((p) => p.label),
      datasets: [{
        data: v.map((p) => p.nbSeances),
        label: 'Séances',
        backgroundColor: this.hexA(this.COL.cyan, 0.55),
        borderRadius: 4,
      }],
    };
  });

  /** Vrai si au moins une semaine a une durée moyenne renseignée. */
  hasDuree = computed<boolean>(() => this.volume().some((p) => p.dureeMoyenneMin != null));

  dureeChart = computed<ChartData<'bar'>>(() => {
    const v = this.volume();
    return {
      labels: v.map((p) => p.label),
      datasets: [{
        data: v.map((p) => p.dureeMoyenneMin ?? 0),
        label: 'Durée moyenne (min)',
        backgroundColor: this.hexA(this.COL.indigo, 0.55),
        borderRadius: 4,
      }],
    };
  });

  muscleChart = computed<ChartData<'doughnut'>>(() => {
    const m = this.muscles();
    const items = m?.muscles ?? [];
    const palette = [
      this.COL.orange, this.COL.cyan, this.COL.indigo, this.COL.green,
      this.COL.violet, this.COL.amber, this.COL.red, '#38bdf8',
      '#fb7185', '#4ade80', '#facc15', '#a78bfa', '#2dd4bf', '#f472b6', '#60a5fa',
    ];
    return {
      labels: items.map((i) => this.muscleLabel(i.muscle)),
      datasets: [{
        data: items.map((i) => i.tonnage),
        backgroundColor: items.map((_, idx) => palette[idx % palette.length]),
        borderColor: '#020617',
        borderWidth: 2,
      }],
    };
  });

  // ----------------------------------------------------------- Corps

  bodyweightChart = computed<ChartData<'line'>>(() => {
    const pts = this.bodyweight()?.points ?? [];
    return {
      labels: pts.map((p) => this.dateLabel(p.date)),
      datasets: [{
        data: pts.map((p) => p.poids),
        label: 'Poids (kg)',
        borderColor: this.COL.green,
        backgroundColor: this.hexA(this.COL.green, 0.15),
        fill: true,
        tension: 0.3,
        pointRadius: 2,
      }],
    };
  });

  // ----------------------------------------------------------- Nutrition

  caloriesChart = computed<ChartData<'line'>>(() => {
    const j = this.nutrition()?.jours ?? [];
    return {
      labels: j.map((p) => this.dateLabel(p.date)),
      datasets: [
        {
          data: j.map((p) => p.kcal),
          label: 'Calories',
          borderColor: this.COL.orange,
          backgroundColor: this.hexA(this.COL.orange, 0.15),
          fill: true,
          tension: 0.3,
          pointRadius: 0,
          spanGaps: true,
        },
        {
          data: j.map((p) => p.targetKcal),
          label: 'Objectif',
          borderColor: this.COL.cyan,
          borderDash: [6, 4],
          fill: false,
          tension: 0,
          pointRadius: 0,
          spanGaps: true,
        },
      ],
    };
  });

  macrosChart = computed<ChartData<'line'>>(() => {
    const j = this.nutrition()?.jours ?? [];
    return {
      labels: j.map((p) => this.dateLabel(p.date)),
      datasets: [
        { data: j.map((p) => p.proteines), label: 'Protéines (g)', borderColor: this.COL.red, backgroundColor: 'transparent', tension: 0.3, pointRadius: 0, spanGaps: true },
        { data: j.map((p) => p.glucides), label: 'Glucides (g)', borderColor: this.COL.amber, backgroundColor: 'transparent', tension: 0.3, pointRadius: 0, spanGaps: true },
        { data: j.map((p) => p.lipides), label: 'Lipides (g)', borderColor: this.COL.indigo, backgroundColor: 'transparent', tension: 0.3, pointRadius: 0, spanGaps: true },
      ],
    };
  });

  macrosDoughnut = computed<ChartData<'doughnut'>>(() => {
    const n = this.nutrition();
    const p = n?.moyProteines ?? 0;
    const g = n?.moyGlucides ?? 0;
    const l = n?.moyLipides ?? 0;
    return {
      labels: ['Protéines', 'Glucides', 'Lipides'],
      datasets: [{
        data: [p, g, l],
        backgroundColor: [this.COL.red, this.COL.amber, this.COL.indigo],
        borderColor: '#020617',
        borderWidth: 2,
      }],
    };
  });

  // ----------------------------------------------------------- Récup

  sleepChart = computed<ChartData<'line'>>(() => {
    const r = this.recoveryChrono();
    return {
      labels: r.map((p) => this.dateLabel(p.date)),
      datasets: [{
        data: r.map((p) => p.sommeilHeures),
        label: 'Sommeil (h)',
        borderColor: this.COL.indigo,
        backgroundColor: this.hexA(this.COL.indigo, 0.15),
        fill: true,
        tension: 0.3,
        pointRadius: 2,
        spanGaps: true,
      }],
    };
  });

  wellbeingChart = computed<ChartData<'line'>>(() => {
    const r = this.recoveryChrono();
    return {
      labels: r.map((p) => this.dateLabel(p.date)),
      datasets: [
        { data: r.map((p) => p.energie), label: 'Énergie', borderColor: this.COL.green, backgroundColor: 'transparent', tension: 0.3, pointRadius: 2, spanGaps: true },
        { data: r.map((p) => p.fatigue), label: 'Fatigue', borderColor: this.COL.red, backgroundColor: 'transparent', tension: 0.3, pointRadius: 2, spanGaps: true },
        { data: r.map((p) => p.stress), label: 'Stress', borderColor: this.COL.amber, backgroundColor: 'transparent', tension: 0.3, pointRadius: 2, spanGaps: true },
        { data: r.map((p) => p.courbatures), label: 'Courbatures', borderColor: this.COL.violet, backgroundColor: 'transparent', tension: 0.3, pointRadius: 2, spanGaps: true },
      ],
    };
  });

  private recoveryChrono(): RecoveryPoint[] {
    return this.recovery().slice().sort((a, b) => a.date.localeCompare(b.date));
  }

  // ----------------------------------------------------------- Options chart

  lineOptions: ChartConfiguration<'line'>['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    interaction: { mode: 'index', intersect: false },
    plugins: { legend: { labels: { color: this.COL.text, boxWidth: 12, font: { size: 11 } } } },
    scales: {
      x: { ticks: { color: this.COL.text, maxRotation: 0, autoSkip: true, maxTicksLimit: 8 }, grid: { color: this.COL.grid } },
      y: { ticks: { color: this.COL.text }, grid: { color: this.COL.grid }, beginAtZero: true },
    },
  };

  barOptions: ChartConfiguration<'bar'>['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: { legend: { labels: { color: this.COL.text, boxWidth: 12, font: { size: 11 } } } },
    scales: {
      x: { ticks: { color: this.COL.text, maxRotation: 0, autoSkip: true, maxTicksLimit: 10 }, grid: { color: this.COL.grid } },
      y: { ticks: { color: this.COL.text }, grid: { color: this.COL.grid }, beginAtZero: true },
    },
  };

  wellbeingOptions: ChartConfiguration<'line'>['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    interaction: { mode: 'index', intersect: false },
    plugins: { legend: { labels: { color: this.COL.text, boxWidth: 12, font: { size: 11 } } } },
    scales: {
      x: { ticks: { color: this.COL.text, maxRotation: 0, autoSkip: true, maxTicksLimit: 8 }, grid: { color: this.COL.grid } },
      y: { ticks: { color: this.COL.text, stepSize: 1 }, grid: { color: this.COL.grid }, min: 0, max: 5 },
    },
  };

  doughnutOptions: ChartConfiguration<'doughnut'>['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: { legend: { position: 'bottom', labels: { color: this.COL.text, boxWidth: 12, font: { size: 11 } } } },
  };

  // ----------------------------------------------------------- Helpers

  goToProfile(): void {
    this.router.navigate(['/profile']);
  }

  tierClass(level: number | undefined | null): string {
    return `tier-${level ?? 1}`;
  }

  hasLiftRecord(e: PerformanceExercise): boolean {
    return e.recordedAt != null;
  }

  /** Étiquette « jj/MM » à partir d'une date/datetime ISO, sans décalage de fuseau. */
  dateLabel(iso: string | null): string {
    if (!iso) return '';
    return iso.slice(8, 10) + '/' + iso.slice(5, 7);
  }

  muscleLabel(key: string): string {
    const map: Record<string, string> = {
      PECTORAUX: 'Pectoraux', DOS: 'Dos', EPAULES: 'Épaules', BICEPS: 'Biceps',
      TRICEPS: 'Triceps', ABDOMINAUX: 'Abdos', QUADRICEPS: 'Quadriceps',
      ISCHIO_JAMBIERS: 'Ischios', FESSIERS: 'Fessiers', MOLLETS: 'Mollets',
      AVANT_BRAS: 'Avant-bras', TRAPEZES: 'Trapèzes', LOMBAIRES: 'Lombaires',
      ADDUCTEURS: 'Adducteurs', ABDUCTEURS: 'Abducteurs', CARDIO: 'Cardio',
    };
    return map[key] ?? key;
  }

  private hexA(hex: string, alpha: number): string {
    const h = hex.replace('#', '');
    const r = parseInt(h.substring(0, 2), 16);
    const g = parseInt(h.substring(2, 4), 16);
    const b = parseInt(h.substring(4, 6), 16);
    return `rgba(${r},${g},${b},${alpha})`;
  }
}
