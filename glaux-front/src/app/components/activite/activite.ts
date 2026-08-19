import { Component, OnInit, signal, computed, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { BaseChartDirective } from 'ng2-charts';
import { ChartConfiguration, ChartData } from 'chart.js';
import { HeaderComponent } from '../shared/header/header';
import { TranslatePipe } from '../../service/translate.pipe';
import { I18nService } from '../../service/i18n.service';
import { GlauxApi, SanteActiviteDto, SanteJourDto, TypeActivite } from '../../service/glaux-api';

type Periode = 7 | 30 | 90 | 365;

@Component({
  selector: 'app-activite',
  standalone: true,
  imports: [CommonModule, BaseChartDirective, HeaderComponent, TranslatePipe],
  templateUrl: './activite.html',
  styleUrl: './activite.css',
})
export class Activite implements OnInit {
  private api = inject(GlauxApi);
  private i18n = inject(I18nService);

  private readonly COL = {
    cyan: '#22d3ee',
    teal: '#2dd4bf',
    amber: '#fbbf24',
    grid: 'rgba(255,255,255,0.06)',
    text: '#7dd3c0',
  };

  readonly periodes: { value: Periode; label: string }[] = [
    { value: 7, label: 'activite.period.7' },
    { value: 30, label: 'activite.period.30' },
    { value: 90, label: 'activite.period.90' },
    { value: 365, label: 'activite.period.365' },
  ];

  periode = signal<Periode>(30);
  jours = signal<SanteJourDto[]>([]);
  loading = signal(true);

  activites = signal<SanteActiviteDto[]>([]);
  activitesLoading = signal(true);

  private readonly typeIcons: Record<TypeActivite, string> = {
    MUSCULATION: 'fitness_center',
    MARCHE: 'directions_walk',
    COURSE: 'directions_run',
    VELO: 'directions_bike',
    FOOTBALL: 'sports_soccer',
    SPORT_AUTRE: 'sports',
  };

  ngOnInit(): void {
    this.load();
    this.loadActivites();
  }

  private loadActivites(): void {
    this.activitesLoading.set(true);
    this.api.getActivites(90).subscribe({
      next: (d) => {
        this.activites.set(d);
        this.activitesLoading.set(false);
      },
      error: () => this.activitesLoading.set(false),
    });
  }

  iconFor(type: TypeActivite): string {
    return this.typeIcons[type] ?? 'sports';
  }

  duree(activite: SanteActiviteDto): string {
    const minutes = Math.round(
      (new Date(activite.endTime).getTime() - new Date(activite.startTime).getTime()) / 60000,
    );
    const h = Math.floor(minutes / 60);
    const m = minutes % 60;
    return h > 0 ? `${h}h${m.toString().padStart(2, '0')}` : `${m}min`;
  }

  zonesActivite(activite: SanteActiviteDto): { key: string; minutes: number; color: string }[] {
    return [
      { key: 'activite.zoneLow', minutes: activite.minutesZoneBasse ?? 0, color: this.COL.cyan },
      { key: 'activite.zoneModerate', minutes: activite.minutesZoneBruleuse ?? 0, color: this.COL.teal },
      { key: 'activite.zoneIntense', minutes: activite.minutesZoneCardio ?? 0, color: this.COL.amber },
      { key: 'activite.zoneMax', minutes: activite.minutesZonePic ?? 0, color: '#f87171' },
    ];
  }

  totalMinutesZonesActivite(activite: SanteActiviteDto): number {
    return this.zonesActivite(activite).reduce((sum, z) => sum + z.minutes, 0) || 1;
  }

  hasZonesActivite(activite: SanteActiviteDto): boolean {
    return this.zonesActivite(activite).some((z) => z.minutes > 0);
  }

  changePeriode(p: Periode): void {
    this.periode.set(p);
    this.load();
  }

  private load(): void {
    this.loading.set(true);
    this.api.getJours(this.periode()).subscribe({
      next: (d) => {
        this.jours.set(d);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  stepsChart = computed<ChartData<'bar'>>(() => {
    const j = this.jours();
    return {
      labels: j.map((p) => this.dateLabel(p.date)),
      datasets: [
        {
          data: j.map((p) => p.pas ?? 0),
          label: this.i18n.t('activite.steps'),
          backgroundColor: this.hexA(this.COL.cyan, 0.55),
          borderRadius: 4,
        },
      ],
    };
  });

  distanceChart = computed<ChartData<'line'>>(() => {
    const j = this.jours();
    return {
      labels: j.map((p) => this.dateLabel(p.date)),
      datasets: [
        {
          data: j.map((p) => (p.distanceM != null ? p.distanceM / 1000 : null)),
          label: this.i18n.t('activite.distance'),
          borderColor: this.COL.teal,
          backgroundColor: this.hexA(this.COL.teal, 0.15),
          fill: true,
          tension: 0.3,
          pointRadius: 0,
          spanGaps: true,
        },
      ],
    };
  });

  caloriesChart = computed<ChartData<'bar'>>(() => {
    const j = this.jours();
    return {
      labels: j.map((p) => this.dateLabel(p.date)),
      datasets: [
        {
          data: j.map((p) => p.caloriesTotales ?? 0),
          label: this.i18n.t('activite.calories'),
          backgroundColor: this.hexA(this.COL.amber, 0.55),
          borderRadius: 4,
        },
      ],
    };
  });

  barOptions: ChartConfiguration<'bar'>['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: { legend: { display: false } },
    scales: {
      x: {
        ticks: { color: this.COL.text, maxRotation: 0, autoSkip: true, maxTicksLimit: 10 },
        grid: { color: this.COL.grid },
      },
      y: { ticks: { color: this.COL.text }, grid: { color: this.COL.grid }, beginAtZero: true },
    },
  };

  lineOptions: ChartConfiguration<'line'>['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    interaction: { mode: 'index', intersect: false },
    plugins: { legend: { display: false } },
    scales: {
      x: {
        ticks: { color: this.COL.text, maxRotation: 0, autoSkip: true, maxTicksLimit: 10 },
        grid: { color: this.COL.grid },
      },
      y: { ticks: { color: this.COL.text }, grid: { color: this.COL.grid }, beginAtZero: true },
    },
  };

  dateLabel(iso: string): string {
    return iso.slice(8, 10) + '/' + iso.slice(5, 7);
  }

  private hexA(hex: string, alpha: number): string {
    const h = hex.replace('#', '');
    const r = parseInt(h.substring(0, 2), 16);
    const g = parseInt(h.substring(2, 4), 16);
    const b = parseInt(h.substring(4, 6), 16);
    return `rgba(${r},${g},${b},${alpha})`;
  }
}
