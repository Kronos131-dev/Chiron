import { Component, OnInit, signal, computed, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { BaseChartDirective } from 'ng2-charts';
import { ChartConfiguration, ChartData } from 'chart.js';
import { HeaderComponent } from '../shared/header/header';
import { TranslatePipe } from '../../service/translate.pipe';
import { I18nService } from '../../service/i18n.service';
import { GlauxApi, SanteAptitudeDto } from '../../service/glaux-api';

@Component({
  selector: 'app-aptitude',
  standalone: true,
  imports: [CommonModule, BaseChartDirective, HeaderComponent, TranslatePipe],
  templateUrl: './aptitude.html',
  styleUrl: './aptitude.css',
})
export class Aptitude implements OnInit {
  private api = inject(GlauxApi);
  private i18n = inject(I18nService);

  private readonly COL = { violet: '#818cf8', grid: 'rgba(255,255,255,0.06)', text: '#7dd3c0' };

  points = signal<SanteAptitudeDto[]>([]);
  loading = signal(true);

  ngOnInit(): void {
    this.api.getAptitude(180).subscribe({
      next: (d) => {
        this.points.set(d);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  dernierVo2Max = computed<number | null>(() => this.dernier((x) => x.vo2Max));

  dernierNiveau = computed<string | null>(() => this.dernier((x) => x.niveauAptitude));

  private dernier<T>(extraire: (point: SanteAptitudeDto) => T | null | undefined): T | null {
    const renseignes = this.points().filter((x) => extraire(x) != null);
    return renseignes.length ? (extraire(renseignes[renseignes.length - 1]) as T) : null;
  }

  chart = computed<ChartData<'line'>>(() => {
    const p = this.points();
    return {
      labels: p.map((x) => this.dateLabel(x.date)),
      datasets: [
        {
          data: p.map((x) => x.vo2Max),
          label: this.i18n.t('aptitude.vo2max'),
          borderColor: this.COL.violet,
          backgroundColor: this.hexA(this.COL.violet, 0.15),
          fill: true,
          tension: 0.3,
          pointRadius: 2,
          spanGaps: true,
        },
      ],
    };
  });

  options: ChartConfiguration<'line'>['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    interaction: { mode: 'index', intersect: false },
    plugins: { legend: { display: false } },
    scales: {
      x: {
        ticks: { color: this.COL.text, maxRotation: 0, autoSkip: true, maxTicksLimit: 8 },
        grid: { color: this.COL.grid },
      },
      y: { ticks: { color: this.COL.text }, grid: { color: this.COL.grid } },
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
