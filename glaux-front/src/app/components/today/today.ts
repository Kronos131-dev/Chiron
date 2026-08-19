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
  private api = inject(GlauxApi);

  resume = signal<SanteResumeDto | null>(null);
  loading = signal(true);
  syncing = signal(false);
  syncEnEchec = signal<SanteSyncEtatDto[]>([]);

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

  km(m: number | null): string {
    return m == null ? '—' : (m / 1000).toFixed(2);
  }
}
