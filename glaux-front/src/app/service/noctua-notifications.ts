import { Injectable, inject, signal } from '@angular/core';
import { GlauxApi } from './glaux-api';

@Injectable({ providedIn: 'root' })
export class NoctuaNotifications {
  private api = inject(GlauxApi);

  readonly nonLus = signal(0);

  rafraichir(): void {
    this.api.getNoctuaNonLus().subscribe({
      next: (r) => this.nonLus.set(r.count),
      error: () => {},
    });
  }
}
