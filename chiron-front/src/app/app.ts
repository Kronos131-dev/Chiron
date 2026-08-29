import { Component, OnDestroy, signal, inject } from '@angular/core';
import { RouterOutlet, RouterLink, RouterLinkActive, Router, NavigationEnd } from '@angular/router';
import { filter } from 'rxjs';
import { PwaUpdateService } from './service/pwa-update.service';
import { preparerCoquilleNative, quitterApplication } from './service/plateforme';
import { TranslatePipe } from './service/translate.pipe';
import { Glissement, brancherGlissement } from './util/glissement-pages';

export interface OngletNav {
  chemin: string;
  icone: string;
  emoji: boolean;
  cle: string;
  exact: boolean;
}

export const ONGLETS_NAV: OngletNav[] = [
  { chemin: '/chat', icone: 'forum', emoji: false, cle: 'nav.coach', exact: true },
  { chemin: '/programme', icone: 'view_list', emoji: false, cle: 'nav.programme', exact: false },
  { chemin: '/journal', icone: 'menu_book', emoji: false, cle: 'nav.journal', exact: false },
  { chemin: '/profile', icone: '👤', emoji: true, cle: 'nav.profil', exact: false },
  { chemin: '/agora', icone: '🏛️', emoji: true, cle: 'nav.agora', exact: false },
  { chemin: '/tresor', icone: 'emoji_events', emoji: false, cle: 'nav.tresor', exact: false },
  {
    chemin: '/bibliotheque',
    icone: 'fitness_center',
    emoji: false,
    cle: 'nav.biblio',
    exact: false,
  },
];

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, TranslatePipe],
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class App implements OnDestroy {
  protected readonly title = signal('chiron-front');
  protected readonly onglets = ONGLETS_NAV;

  /**
   * Routes « plein écran » (authentification / onboarding) sur lesquelles la barre
   * de navigation globale ne doit pas s'afficher : fixée en bas, elle recouvrirait
   * sinon les boutons propres à ces pages (visible surtout en zoom).
   */
  private static readonly NAV_HIDDEN_PREFIXES = ['/login', '/reset-password', '/onboarding'];
  protected readonly showNav = signal(this.computeShowNav(location.pathname));

  private readonly pwaUpdate = inject(PwaUpdateService);
  private readonly router = inject(Router);
  private readonly glissement: Glissement;
  private url = location.pathname;

  constructor() {
    this.router.events
      .pipe(filter((e): e is NavigationEnd => e instanceof NavigationEnd))
      .subscribe((e) => {
        this.url = e.urlAfterRedirects;
        this.showNav.set(this.computeShowNav(e.urlAfterRedirects));
      });
    // Active automatiquement les nouvelles versions de la PWA (plus de réinstallation).
    this.pwaUpdate.init();
    preparerCoquilleNative(quitterApplication);
    this.glissement = brancherGlissement(document, (sens) => this.changerDePage(sens));
  }

  ngOnDestroy(): void {
    this.glissement.detacher();
  }

  // WHY: le glissement ne vaut qu'entre les pages de la barre. Ailleurs — une séance en cours,
  // une course, un formulaire — un mouvement horizontal appartient à la page, et l'emporter
  // vers une autre ferait perdre ce qui y est saisi.
  private changerDePage(sens: 1 | -1): void {
    const index = ONGLETS_NAV.findIndex((onglet) => this.url.startsWith(onglet.chemin));
    if (index < 0) return;
    const suivant = index + sens;
    if (suivant < 0 || suivant >= ONGLETS_NAV.length) return;
    this.router.navigate([ONGLETS_NAV[suivant].chemin]);
  }

  private computeShowNav(url: string): boolean {
    return !App.NAV_HIDDEN_PREFIXES.some((p) => url.startsWith(p));
  }
}
