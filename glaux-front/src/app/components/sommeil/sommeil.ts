import { Component, OnInit, signal, computed, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HeaderComponent } from '../shared/header/header';
import { TranslatePipe } from '../../service/translate.pipe';
import { GlauxApi, SanteSommeilDto } from '../../service/glaux-api';

@Component({
  selector: 'app-sommeil',
  standalone: true,
  imports: [CommonModule, HeaderComponent, TranslatePipe],
  templateUrl: './sommeil.html',
  styleUrl: './sommeil.css',
})
export class Sommeil implements OnInit {
  private api = inject(GlauxApi);

  nights = signal<SanteSommeilDto[]>([]);
  loading = signal(true);

  ngOnInit(): void {
    this.api.getSommeil(30).subscribe({
      next: (d) => {
        this.nights.set(d.slice().reverse());
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  avgScore = computed<number | null>(() => this.avg(this.nights().map((n) => n.score)));
  avgDuration = computed<number | null>(() => this.avg(this.nights().map((n) => n.minutesEndormi)));

  private avg(values: (number | null)[]): number | null {
    const v = values.filter((x): x is number => x != null);
    if (!v.length) return null;
    return Math.round(v.reduce((a, b) => a + b, 0) / v.length);
  }

  composition(n: SanteSommeilDto): { key: string; minutes: number; color: string }[] {
    return [
      { key: 'sommeil.deep', minutes: n.minutesProfond ?? 0, color: '#0891b2' },
      { key: 'sommeil.light', minutes: n.minutesLeger ?? 0, color: '#22d3ee' },
      { key: 'sommeil.rem', minutes: n.minutesParadoxal ?? 0, color: '#818cf8' },
      { key: 'sommeil.awake', minutes: n.minutesEveille ?? 0, color: '#7dd3c0' },
      { key: 'sommeil.restless', minutes: n.minutesAgite ?? 0, color: '#f87171' },
    ];
  }

  totalMinutes(n: SanteSommeilDto): number {
    return this.composition(n).reduce((sum, c) => sum + c.minutes, 0) || 1;
  }

  duree(min: number | null): string {
    if (min == null) return '—';
    const h = Math.floor(min / 60);
    const m = min % 60;
    return `${h}h${m.toString().padStart(2, '0')}`;
  }

  dateLabel(iso: string): string {
    return iso.slice(8, 10) + '/' + iso.slice(5, 7);
  }
}
