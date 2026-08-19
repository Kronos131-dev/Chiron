import { Injectable, inject } from '@angular/core';
import { SwUpdate, VersionReadyEvent } from '@angular/service-worker';
import { filter } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class PwaUpdateService {
  private readonly swUpdate = inject(SwUpdate);

  init(): void {
    if (!this.swUpdate.isEnabled) return;

    this.swUpdate.versionUpdates
      .pipe(filter((e): e is VersionReadyEvent => e.type === 'VERSION_READY'))
      .subscribe(() => {
        this.swUpdate.activateUpdate().then(() => document.location.reload());
      });

    // WHY: sans vérification périodique le service worker sert indéfiniment la version
    // en cache et l'utilisateur doit désinstaller l'application pour voir un déploiement.
    // Les en-têtes no-cache posés par glaux-nginx.conf sur ngsw.json et index.html sont
    // ce qui rend cette détection fiable.
    this.verifier();
    setInterval(() => this.verifier(), 60 * 1000);
    document.addEventListener('visibilitychange', () => {
      if (document.visibilityState === 'visible') this.verifier();
    });
  }

  private verifier(): void {
    this.swUpdate.checkForUpdate().catch(() => undefined);
  }
}
