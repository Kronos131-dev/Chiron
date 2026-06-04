import { Injectable, inject } from '@angular/core';
import { SwUpdate, VersionReadyEvent } from '@angular/service-worker';
import { filter } from 'rxjs';

/**
 * Applique automatiquement les nouvelles versions de la PWA.
 *
 * Sans ça, le service worker Angular sert indéfiniment la version mise en cache et
 * l'utilisateur doit désinstaller/réinstaller. Ici : dès qu'une nouvelle version est
 * prête, on l'active et on recharge ; on vérifie aussi régulièrement et au retour en
 * avant-plan (les en-têtes no-cache sur ngsw.json/index.html rendent la détection fiable).
 */
@Injectable({ providedIn: 'root' })
export class PwaUpdateService {
  private readonly swUpdate = inject(SwUpdate);

  init(): void {
    if (!this.swUpdate.isEnabled) return;

    // Nouvelle version téléchargée → on l'active et on recharge.
    this.swUpdate.versionUpdates
      .pipe(filter((e): e is VersionReadyEvent => e.type === 'VERSION_READY'))
      .subscribe(() => {
        this.swUpdate.activateUpdate().then(() => document.location.reload());
      });

    // Vérifie au démarrage, puis périodiquement et au retour en avant-plan.
    this.check();
    setInterval(() => this.check(), 60 * 1000);
    document.addEventListener('visibilitychange', () => {
      if (document.visibilityState === 'visible') this.check();
    });
  }

  private check(): void {
    this.swUpdate.checkForUpdate().catch(() => {
      /* hors-ligne ou SW indisponible : on réessaiera au prochain tick. */
    });
  }
}
