import { Component, signal, inject } from '@angular/core';
import { RouterOutlet, RouterLink, RouterLinkActive, Router, NavigationEnd } from '@angular/router';
import { filter } from 'rxjs';
import { PwaUpdateService } from './service/pwa-update.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App {
  protected readonly title = signal('chiron-front');

  /**
   * Routes « plein écran » (authentification / onboarding) sur lesquelles la barre
   * de navigation globale ne doit pas s'afficher : fixée en bas, elle recouvrirait
   * sinon les boutons propres à ces pages (visible surtout en zoom).
   */
  private static readonly NAV_HIDDEN_PREFIXES = ['/login', '/reset-password', '/onboarding'];
  protected readonly showNav = signal(this.computeShowNav(location.pathname));

  private readonly pwaUpdate = inject(PwaUpdateService);

  constructor(router: Router) {
    router.events
      .pipe(filter((e): e is NavigationEnd => e instanceof NavigationEnd))
      .subscribe((e) => this.showNav.set(this.computeShowNav(e.urlAfterRedirects)));
    // Active automatiquement les nouvelles versions de la PWA (plus de réinstallation).
    this.pwaUpdate.init();
  }

  private computeShowNav(url: string): boolean {
    return !App.NAV_HIDDEN_PREFIXES.some((p) => url.startsWith(p));
  }
}
