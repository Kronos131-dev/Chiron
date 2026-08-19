import { Component, OnInit, signal, computed, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { BaseChartDirective } from 'ng2-charts';
import { ChartConfiguration, ChartData } from 'chart.js';
import { HeaderComponent } from '../shared/header/header';
import { TranslatePipe } from '../../service/translate.pipe';
import { I18nService } from '../../service/i18n.service';
import {
  GlauxApi,
  SanteFcPointDto,
  SanteJourDto,
  SanteCardioHebdoDto,
} from '../../service/glaux-api';

@Component({
  selector: 'app-coeur',
  standalone: true,
  imports: [CommonModule, FormsModule, BaseChartDirective, HeaderComponent, TranslatePipe],
  templateUrl: './coeur.html',
  styleUrl: './coeur.css',
})
export class Coeur implements OnInit {
  private api = inject(GlauxApi);
  private i18n = inject(I18nService);

  private readonly COL = {
    cyan: '#22d3ee',
    teal: '#2dd4bf',
    grid: 'rgba(255,255,255,0.06)',
    text: '#7dd3c0',
  };

  selectedDate = signal(new Date().toISOString().slice(0, 10));
  fcPoints = signal<SanteFcPointDto[]>([]);
  jours = signal<SanteJourDto[]>([]);
  cardioHebdo = signal<SanteCardioHebdoDto[]>([]);
  loadingFc = signal(true);

  ngOnInit(): void {
    this.loadFc();
    this.api.getJours(14).subscribe({ next: (d) => this.jours.set(d), error: () => {} });
    this.api
      .getCardioHebdo(12)
      .subscribe({ next: (d) => this.cardioHebdo.set(d), error: () => {} });
  }

  onDateChange(date: string): void {
    this.selectedDate.set(date);
    this.loadFc();
  }

  private loadFc(): void {
    this.loadingFc.set(true);
    this.api.getFrequenceCardiaqueJour(this.selectedDate()).subscribe({
      next: (d) => {
        this.fcPoints.set(d);
        this.loadingFc.set(false);
      },
      error: () => {
        this.fcPoints.set([]);
        this.loadingFc.set(false);
      },
    });
  }

  latestFcRepos = computed<number | null>(() => {
    const j = this.jours().filter((d) => d.fcRepos != null);
    return j.length ? j[j.length - 1].fcRepos : null;
  });

  latestVfc = computed<number | null>(() => {
    const j = this.jours().filter((d) => d.vfcMs != null);
    return j.length ? j[j.length - 1].vfcMs : null;
  });

  latestCardioHebdo = computed<SanteCardioHebdoDto | null>(() => {
    const c = this.cardioHebdo();
    return c.length ? c[c.length - 1] : null;
  });

  fcChart = computed<ChartData<'line'>>(() => {
    const pts = this.fcPoints();
    const labels = pts.map((p) => p.horodatage.slice(11, 16));
    return {
      labels,
      datasets: [
        {
          data: pts.map((p) => p.fcMin),
          label: 'min',
          borderColor: 'transparent',
          backgroundColor: 'transparent',
          pointRadius: 0,
          fill: false,
        },
        {
          data: pts.map((p) => p.fcMax),
          label: 'max',
          borderColor: 'transparent',
          backgroundColor: this.hexA(this.COL.cyan, 0.15),
          pointRadius: 0,
          fill: '-1',
        },
        {
          data: pts.map((p) => p.fcMoyenne),
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

  cardioHebdoChart = computed<ChartData<'bar'>>(() => {
    const c = this.cardioHebdo();
    return {
      labels: c.map((p) => this.dateLabel(p.semaineDebut)),
      datasets: [
        {
          data: c.map((p) => p.chargeCardio ?? 0),
          label: this.i18n.t('coeur.weeklyCardio'),
          backgroundColor: this.hexA(this.COL.teal, 0.55),
          borderRadius: 4,
        },
      ],
    };
  });

  lineOptions: ChartConfiguration<'line'>['options'] = {
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

  barOptions: ChartConfiguration<'bar'>['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: { legend: { display: false } },
    scales: {
      x: { ticks: { color: this.COL.text, maxRotation: 0 }, grid: { color: this.COL.grid } },
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
