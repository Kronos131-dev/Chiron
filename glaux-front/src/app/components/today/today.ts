import { Component, OnInit, signal, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HeaderComponent } from '../shared/header/header';
import { TranslatePipe } from '../../service/translate.pipe';
import { GlauxApi, SanteResumeDto, SanteSyncEtatDto } from '../../service/glaux-api';

@Component({
  selector: 'app-today',
  standalone: true,
  imports: [CommonModule, HeaderComponent, TranslatePipe],
  templateUrl: './today.html',
  styleUrl: './today.css',
})
export class Today implements OnInit {
  private static readonly PULL_THRESHOLD_PX = 70;
  private static readonly PULL_MAX_PX = 100;

  private api = inject(GlauxApi);

  resume = signal<SanteResumeDto | null>(null);
  loading = signal(true);
  syncing = signal(false);
  syncEnEchec = signal<SanteSyncEtatDto[]>([]);

  pullDistance = signal(0);

  private pullActive = false;
  private touchStartY = 0;

  ngOnInit(): void {
    this.load();
    this.chargerEtatSync();
  }

  private load(): void {
    this.loading.set(true);
    this.api.getResume().subscribe({
      next: (r) => {
        this.resume.set(r);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  private chargerEtatSync(): void {
    this.api.getSyncEtat().subscribe({
      next: (etats) => this.retenirEchecs(etats),
      error: () => this.syncEnEchec.set([]),
    });
  }

  private retenirEchecs(etats: SanteSyncEtatDto[]): void {
    this.syncEnEchec.set(etats.filter((e) => e.statut !== 'OK'));
  }

  sync(): void {
    this.syncing.set(true);
    this.api.forcerSync().subscribe({
      next: (etats) => {
        this.retenirEchecs(etats);
        this.syncing.set(false);
        this.load();
      },
      error: () => this.syncing.set(false),
    });
  }

  onTouchStart(event: TouchEvent): void {
    if (window.scrollY > 0 || this.syncing()) return;
    this.pullActive = true;
    this.touchStartY = event.touches[0].clientY;
  }

  onTouchMove(event: TouchEvent): void {
    if (!this.pullActive) return;
    const delta = event.touches[0].clientY - this.touchStartY;
    this.pullDistance.set(delta > 0 ? Math.min(delta, Today.PULL_MAX_PX) : 0);
  }

  onTouchEnd(): void {
    if (!this.pullActive) return;
    this.pullActive = false;
    const triggered = this.pullDistance() >= Today.PULL_THRESHOLD_PX;
    this.pullDistance.set(0);
    if (triggered) this.sync();
  }

  // WHY: les trois seuils sont ceux de Google Health — 1-29 faible, 30-64 modérée,
  // 65-100 élevée — pour que la lecture reste comparable d'une application à l'autre.
  libellePreparation(score: number): string {
    if (score >= 65) return 'today.readinessHigh';
    if (score >= 30) return 'today.readinessModerate';
    return 'today.readinessLow';
  }

  couleurPreparation(score: number | null): string {
    if (score == null) return 'text-white';
    if (score >= 65) return 'text-emerald-400';
    if (score >= 30) return 'text-sky-400';
    return 'text-orange-400';
  }

  km(m: number | null): string {
    return m == null ? '—' : (m / 1000).toFixed(2);
  }
}
