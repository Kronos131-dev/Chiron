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
  joursCardio = signal<SanteJourDto[]>([]);
  cardioHebdo = signal<SanteCardioHebdoDto[]>([]);
  loadingFc = signal(true);

  ngOnInit(): void {
    this.loadFc();
    this.api.getJours(14).subscribe({ next: (d) => this.jours.set(d), error: () => {} });
    this.api.getJours(84).subscribe({ next: (d) => this.joursCardio.set(d), error: () => {} });
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

  private readonly SEMAINES_DISPONIBLES = 12;

  semaineOffset = signal(0);

  private joursDeLaSemaine = computed<SanteJourDto[]>(() => {
    const parDate = new Map(this.joursCardio().map((j) => [j.date, j]));
    return this.datesDeLaSemaine().map(
      (iso) => parDate.get(iso) ?? ({ date: iso, chargeCardio: null } as SanteJourDto),
    );
  });

  private datesDeLaSemaine = computed<string[]>(() => {
    const lundi = this.lundiDeLaSemaine(this.semaineOffset());
    return Array.from({ length: 7 }, (_, i) => {
      const d = new Date(lundi);
      d.setDate(lundi.getDate() + i);
      return this.enIso(d);
    });
  });

  chargeDeLaSemaine = computed<number | null>(() => {
    const charges = this.joursDeLaSemaine()
      .map((j) => j.chargeCardio)
      .filter((c): c is number => c != null);
    return charges.length ? charges.reduce((a, b) => a + b, 0) : null;
  });

  libelleSemaine = computed<string>(() => {
    const dates = this.datesDeLaSemaine();
    return `${this.dateLabel(dates[0])} – ${this.dateLabel(dates[6])}`;
  });

  estSemaineCourante = computed<boolean>(() => this.semaineOffset() === 0);

  peutReculer = computed<boolean>(() => this.semaineOffset() < this.SEMAINES_DISPONIBLES - 1);

  semainePrecedente(): void {
    if (this.peutReculer()) this.semaineOffset.set(this.semaineOffset() + 1);
  }

  semaineSuivante(): void {
    if (!this.estSemaineCourante()) this.semaineOffset.set(this.semaineOffset() - 1);
  }

  cardioHebdoChart = computed<ChartData<'bar'>>(() => {
    const jours = this.joursDeLaSemaine();
    return {
      labels: jours.map((j) => this.initialeJour(j.date)),
      datasets: [
        {
          data: jours.map((j) => j.chargeCardio ?? 0),
          label: this.i18n.t('coeur.weeklyCardio'),
          backgroundColor: this.hexA(this.COL.teal, 0.55),
          borderRadius: 4,
        },
      ],
    };
  });

  private lundiDeLaSemaine(offset: number): Date {
    const d = new Date();
    d.setHours(0, 0, 0, 0);
    const depuisLundi = (d.getDay() + 6) % 7;
    d.setDate(d.getDate() - depuisLundi - 7 * offset);
    return d;
  }

  private enIso(d: Date): string {
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(
      d.getDate(),
    ).padStart(2, '0')}`;
  }

  private initialeJour(iso: string): string {
    return new Date(iso + 'T12:00:00').toLocaleDateString(this.i18n.lang() === 'en' ? 'en' : 'fr', {
      weekday: 'narrow',
    });
  }

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
