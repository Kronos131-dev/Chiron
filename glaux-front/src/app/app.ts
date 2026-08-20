import { Component, HostListener, inject, signal } from '@angular/core';
import { Router, RouterOutlet } from '@angular/router';
import { PwaUpdateService } from './service/pwa-update.service';

const SWIPE_TAB_ORDER = ['/noctua', '/', '/coeur', '/sommeil', '/activite'];
const SWIPE_MIN_HORIZONTAL_PX = 60;
const SWIPE_MAX_VERTICAL_PX = 60;

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet],
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class App {
  protected readonly title = signal('Noctua');

  private readonly pwaUpdate = inject(PwaUpdateService);
  private readonly router = inject(Router);

  private touchStartX = 0;
  private touchStartY = 0;

  constructor() {
    this.pwaUpdate.init();
  }

  @HostListener('touchstart', ['$event'])
  onTouchStart(event: TouchEvent): void {
    this.touchStartX = event.touches[0].clientX;
    this.touchStartY = event.touches[0].clientY;
  }

  @HostListener('touchend', ['$event'])
  onTouchEnd(event: TouchEvent): void {
    const touch = event.changedTouches[0];
    const deltaX = touch.clientX - this.touchStartX;
    const deltaY = touch.clientY - this.touchStartY;

    if (Math.abs(deltaX) < SWIPE_MIN_HORIZONTAL_PX || Math.abs(deltaY) > SWIPE_MAX_VERTICAL_PX) {
      return;
    }

    const currentPath = this.router.url.split('?')[0].split('#')[0];
    const currentIndex = SWIPE_TAB_ORDER.indexOf(currentPath);
    if (currentIndex === -1) return;

    // WHY: swipe de droite à gauche fait avancer le contenu vers la gauche, donc découvre
    // l'onglet suivant (à droite dans la barre) ; le geste inverse recule d'un onglet.
    const nextIndex = deltaX < 0 ? currentIndex + 1 : currentIndex - 1;
    if (nextIndex < 0 || nextIndex >= SWIPE_TAB_ORDER.length) return;

    this.router.navigateByUrl(SWIPE_TAB_ORDER[nextIndex]);
  }
}
