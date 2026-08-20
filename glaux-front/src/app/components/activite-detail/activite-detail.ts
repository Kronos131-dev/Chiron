import { Component, OnInit, signal, computed, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { BaseChartDirective } from 'ng2-charts';
import { ChartConfiguration, ChartData, Plugin } from 'chart.js';
import { HeaderComponent } from '../shared/header/header';
import { TranslatePipe } from '../../service/translate.pipe';
import { I18nService } from '../../service/i18n.service';
import {
  GlauxApi,
  NoctuaBriefingDto,
  SanteActiviteDetailDto,
  TypeActivite,
} from '../../service/glaux-api';

@Component({
  selector: 'app-activite-detail',
  standalone: true,
  imports: [CommonModule, BaseChartDirective, HeaderComponent, TranslatePipe],
  templateUrl: './activite-detail.html',
  styleUrl: './activite-detail.css',
})
export class ActiviteDetail implements OnInit {
  private api = inject(GlauxApi);
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private i18n = inject(I18nService);

  private readonly COL = {
    cyan: '#22d3ee',
    teal: '#2dd4bf',
    amber: '#fbbf24',
    rouge: '#f87171',
    grid: 'rgba(255,255,255,0.06)',
    text: '#7dd3c0',
    seuil: 'rgba(240,253,250,0.35)',
  };

  private readonly typeIcons: Record<TypeActivite, string> = {
    MUSCULATION: 'fitness_center',
    MARCHE: 'directions_walk',
    COURSE: 'directions_run',
    VELO: 'directions_bike',
    FOOTBALL: 'sports_soccer',
    SPORT_AUTRE: 'sports',
  };

  detail = signal<SanteActiviteDetailDto | null>(null);
  loading = signal(true);
  notFound = signal(false);

  briefing = signal<NoctuaBriefingDto | null>(null);

  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    this.loading.set(true);
    this.api.getActiviteDetail(id).subscribe({
      next: (d) => {
        this.detail.set(d);
        this.loading.set(false);
      },
      error: () => {
        this.notFound.set(true);
        this.loading.set(false);
      },
    });
    this.api.getNoctuaBriefingParActivite(id).subscribe({
      next: (b) => this.briefing.set(b),
      error: () => this.briefing.set(null),
    });
  }

  retour(): void {
    this.router.navigate(['/activite']);
  }

  ouvrirBriefing(): void {
    const b = this.briefing();
    if (b) this.router.navigate(['/noctua', b.id]);
  }

  iconFor(type: TypeActivite): string {
    return this.typeIcons[type] ?? 'sports';
  }

  duree(): string {
    const d = this.detail();
    if (!d) return '';
    const minutes = Math.round(
      (new Date(d.activite.endTime).getTime() - new Date(d.activite.startTime).getTime()) / 60000,
    );
    const h = Math.floor(minutes / 60);
    const m = minutes % 60;
    return h > 0 ? `${h}h${m.toString().padStart(2, '0')}` : `${m}min`;
  }

  dateLabel(iso: string): string {
    return iso.slice(8, 10) + '/' + iso.slice(5, 7);
  }

  heureLabel(iso: string): string {
    return iso.slice(11, 16);
  }

  zones = computed<{ key: string; minutes: number; color: string }[]>(() => {
    const a = this.detail()?.activite;
    if (!a) return [];
    return [
      { key: 'activite.zoneLow', minutes: a.minutesZoneBasse ?? 0, color: this.COL.cyan },
      { key: 'activite.zoneModerate', minutes: a.minutesZoneBruleuse ?? 0, color: this.COL.teal },
      { key: 'activite.zoneIntense', minutes: a.minutesZoneCardio ?? 0, color: this.COL.amber },
      { key: 'activite.zoneMax', minutes: a.minutesZonePic ?? 0, color: this.COL.rouge },
    ];
  });

  totalMinutesZones = computed<number>(
    () => this.zones().reduce((sum, z) => sum + z.minutes, 0) || 1,
  );

  hasZones = computed<boolean>(() => this.zones().some((z) => z.minutes > 0));

  private minutesDuJour(iso: string): number {
    return Number(iso.slice(11, 13)) * 60 + Number(iso.slice(14, 16));
  }

  private depuisMinutesDuJour(total: number): string {
    const h = Math.floor(total / 60) % 24;
    const m = total % 60;
    return `${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}`;
  }

  private fenetreLabels(): string[] {
    const a = this.detail()?.activite;
    if (!a) return [];
    const debut = Math.floor(this.minutesDuJour(a.startTime) / 5) * 5;
    const fin = Math.ceil(this.minutesDuJour(a.endTime) / 5) * 5;
    const labels: string[] = [];
    for (let m = debut; m <= fin; m += 5) {
      labels.push(this.depuisMinutesDuJour(m));
    }
    return labels;
  }

  private readonly seuilsFcPlugin: Plugin<'line'> = {
    id: 'seuilsFc',
    afterDatasetsDraw: (chart) => {
      const { ctx, chartArea, scales } = chart;
      const y = scales['y'];
      const seuils = this.detail()?.seuils;
      if (!chartArea || !y || !seuils) return;

      const paliers = [
        { valeur: seuils.modere, cle: 'coeur.zoneModeree' },
        { valeur: seuils.intense, cle: 'coeur.zoneIntense' },
        { valeur: seuils.maximum, cle: 'coeur.zoneMaximale' },
      ];

      ctx.save();
      ctx.strokeStyle = this.COL.seuil;
      ctx.fillStyle = this.COL.seuil;
      ctx.font = '10px sans-serif';
      ctx.textAlign = 'right';
      ctx.setLineDash([4, 4]);

      for (const palier of paliers) {
        if (palier.valeur < y.min || palier.valeur > y.max) continue;
        const pixelY = y.getPixelForValue(palier.valeur);
        ctx.beginPath();
        ctx.moveTo(chartArea.left, pixelY);
        ctx.lineTo(chartArea.right, pixelY);
        ctx.stroke();
        ctx.fillText(this.i18n.t(palier.cle), chartArea.right, pixelY - 4);
      }

      ctx.restore();
    },
  };

  fcPlugins = [this.seuilsFcPlugin];

  fcChart = computed<ChartData<'line'>>(() => {
    const d = this.detail();
    const labels = this.fenetreLabels();
    if (!d) return { labels, datasets: [] };
    const parLabel = new Map(
      d.pointsFrequenceCardiaque.map((p) => [p.horodatage.slice(11, 16), p]),
    );
    return {
      labels,
      datasets: [
        {
          data: labels.map((l) => parLabel.get(l)?.fcMin ?? null),
          label: 'min',
          borderColor: 'transparent',
          backgroundColor: 'transparent',
          pointRadius: 0,
          fill: false,
        },
        {
          data: labels.map((l) => parLabel.get(l)?.fcMax ?? null),
          label: 'max',
          borderColor: 'transparent',
          backgroundColor: this.hexA(this.COL.cyan, 0.15),
          pointRadius: 0,
          fill: '-1',
        },
        {
          data: labels.map((l) => parLabel.get(l)?.fcMoyenne ?? null),
          label: this.i18n.t('coeur.heartRateToday'),
          borderColor: this.COL.cyan,
          backgroundColor: 'transparent',
          pointRadius: 0,
          tension: 0.3,
          fill: false,
        },
      ],
    };
  });

  lineOptions = computed<ChartConfiguration<'line'>['options']>(() => {
    const seuils = this.detail()?.seuils;
    return {
      responsive: true,
      maintainAspectRatio: false,
      interaction: { mode: 'index', intersect: false },
      plugins: { legend: { display: false } },
      scales: {
        x: {
          ticks: { color: this.COL.text, maxRotation: 0, autoSkip: true, maxTicksLimit: 6 },
          grid: { color: this.COL.grid },
        },
        y: {
          ticks: { color: this.COL.text },
          grid: { color: this.COL.grid },
          suggestedMax: seuils ? seuils.maximum + 10 : undefined,
        },
      },
    };
  });

  private hexA(hex: string, alpha: number): string {
    const h = hex.replace('#', '');
    const r = parseInt(h.substring(0, 2), 16);
    const g = parseInt(h.substring(2, 4), 16);
    const b = parseInt(h.substring(4, 6), 16);
    return `rgba(${r},${g},${b},${alpha})`;
  }
}
