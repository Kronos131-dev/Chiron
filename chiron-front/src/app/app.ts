import { Component, signal } from '@angular/core';
import { RouterOutlet, RouterLink, RouterLinkActive, Router, NavigationEnd } from '@angular/router';
import { filter } from 'rxjs';

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

  constructor(router: Router) {
    router.events
      .pipe(filter((e): e is NavigationEnd => e instanceof NavigationEnd))
      .subscribe((e) => this.showNav.set(this.computeShowNav(e.urlAfterRedirects)));
  }

  private computeShowNav(url: string): boolean {
    return !App.NAV_HIDDEN_PREFIXES.some((p) => url.startsWith(p));
  }
}
