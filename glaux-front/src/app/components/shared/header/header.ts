import { Component, Input, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { AuthService } from '../../../service/auth.service';
import { TranslatePipe } from '../../../service/translate.pipe';
import { NoctuaNotifications } from '../../../service/noctua-notifications';
import { environment } from '../../../../environments/environment';

@Component({
  selector: 'app-header',
  standalone: true,
  imports: [CommonModule, RouterLink, RouterLinkActive, TranslatePipe],
  templateUrl: './header.html',
})
export class HeaderComponent implements OnInit {
  @Input() title: string = 'Noctua';

  showMenu = signal(false);
  readonly chironUrl = environment.chironUrl;

  constructor(
    private authService: AuthService,
    public noctuaNotifications: NoctuaNotifications,
  ) {}

  ngOnInit(): void {
    this.noctuaNotifications.rafraichir();
  }

  toggleMenu(): void {
    this.showMenu.update((v) => !v);
  }

  togglePush(): void {
    const action = this.noctuaNotifications.pushActif()
      ? this.noctuaNotifications.desactiverPush()
      : this.noctuaNotifications.activerPush();
    action.catch(() => {});
  }

  logout(): void {
    this.authService.logout();
  }
}
