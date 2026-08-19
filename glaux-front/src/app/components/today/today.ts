import { Component, OnInit, signal, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HeaderComponent } from '../shared/header/header';
import { TranslatePipe } from '../../service/translate.pipe';
import { GlauxApi, SanteResumeDto } from '../../service/glaux-api';

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

  ngOnInit(): void {
    this.load();
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

  sync(): void {
    this.syncing.set(true);
    this.api.forcerSync().subscribe({
      next: () => {
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
