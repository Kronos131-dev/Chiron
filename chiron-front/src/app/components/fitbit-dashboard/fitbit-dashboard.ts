import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { ChironApi, FitbitDashboard as FitbitDashboardData } from '../../service/chiron-api';
import { HeaderComponent } from '../shared/header/header';

/**
 * Tableau de bord des données santé Fitbit : activité du jour, sommeil,
 * fréquence cardiaque, et mini-graphes en barres sur la semaine.
 */
@Component({
  selector: 'app-fitbit-dashboard',
  standalone: true,
  imports: [CommonModule, HeaderComponent],
  templateUrl: './fitbit-dashboard.html',
  styleUrls: ['./fitbit-dashboard.css']
})
export class FitbitDashboard implements OnInit {

  dashboard = signal<FitbitDashboardData | null>(null);
  isLoading = signal(true);
  error = signal<string | null>(null);

  constructor(private chironApi: ChironApi, private router: Router) {}

  ngOnInit() {
    this.load();
  }

  load() {
    this.isLoading.set(true);
    this.error.set(null);
    this.chironApi.getFitbitDashboard(7).subscribe({
      next: (d) => {
        this.dashboard.set(d);
        this.isLoading.set(false);
      },
      error: () => {
        this.error.set('Impossible de charger les données Fitbit.');
        this.isLoading.set(false);
      }
    });
  }

  goToProfile() {
    this.router.navigate(['/profile']);
  }

  /** Valeur max de pas sur la période, plancher à 1 pour éviter la division par zéro. */
  maxSteps(): number {
    const days = this.dashboard()?.days ?? [];
    return Math.max(1, ...days.map(d => d.steps ?? 0));
  }

  /** Valeur max de sommeil sur la période, plancher à 1. */
  maxSleep(): number {
    const days = this.dashboard()?.days ?? [];
    return Math.max(1, ...days.map(d => d.sleepHours ?? 0));
  }

  /** Hauteur d'une barre (en %) proportionnelle au max ; minimum visible 4 %. */
  barHeight(value: number | null, max: number): string {
    if (!value || value <= 0) return '4%';
    return Math.max(4, Math.round((value / max) * 100)) + '%';
  }

  /** Étiquette JJ/MM extraite d'une date ISO, sans décalage de fuseau. */
  dayLabel(dateIso: string): string {
    return dateIso.slice(8, 10) + '/' + dateIso.slice(5, 7);
  }
}
