import { Injectable, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { SwPush } from '@angular/service-worker';
import { firstValueFrom } from 'rxjs';
import { GlauxApi } from './glaux-api';

@Injectable({ providedIn: 'root' })
export class NoctuaNotifications {
  private api = inject(GlauxApi);
  private swPush = inject(SwPush, { optional: true });
  private router = inject(Router);

  readonly nonLus = signal(0);
  readonly pushActif = signal(false);
  readonly pushDisponible = this.swPush?.isEnabled ?? false;

  private endpointActif: string | null = null;

  constructor() {
    if (!this.pushDisponible) return;

    this.swPush!.subscription.subscribe((subscription) => {
      this.endpointActif = subscription?.endpoint ?? null;
      this.pushActif.set(subscription !== null);
    });

    this.swPush!.notificationClicks.subscribe(({ notification }) => {
      const data = notification.data as
        { onActionClick?: { default?: { url?: string } } } | undefined;
      const url = data?.onActionClick?.default?.url;
      if (url) this.router.navigateByUrl(url);
    });
  }

  rafraichir(): void {
    this.api.getNoctuaNonLus().subscribe({
      next: (r) => this.nonLus.set(r.count),
      error: () => {},
    });
  }

  async activerPush(): Promise<void> {
    if (!this.pushDisponible) return;
    const { clePublique } = await firstValueFrom(this.api.getVapidClePublique());
    const subscription = await this.swPush!.requestSubscription({ serverPublicKey: clePublique });
    const json = subscription.toJSON();
    if (!json.endpoint || !json.keys) return;
    await firstValueFrom(
      this.api.abonnerPush({
        endpoint: json.endpoint,
        keys: { p256dh: json.keys['p256dh'], auth: json.keys['auth'] },
      }),
    );
    this.pushActif.set(true);
  }

  async desactiverPush(): Promise<void> {
    if (!this.pushDisponible) return;
    const endpoint = this.endpointActif;
    await this.swPush!.unsubscribe();
    this.pushActif.set(false);
    if (endpoint) {
      await firstValueFrom(this.api.desabonnerPush(endpoint));
    }
  }
}
