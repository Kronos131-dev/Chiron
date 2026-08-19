import { Component, Input, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { AuthService } from '../../../service/auth.service';
import { TranslatePipe } from '../../../service/translate.pipe';
import { environment } from '../../../../environments/environment';

@Component({
  selector: 'app-header',
  standalone: true,
  imports: [CommonModule, RouterLink, RouterLinkActive, TranslatePipe],
  templateUrl: './header.html',
})
export class HeaderComponent {
  @Input() title: string = 'Noctua';

  showMenu = signal(false);
  readonly chironUrl = environment.chironUrl;

  constructor(private authService: AuthService) {}

  toggleMenu(): void {
    this.showMenu.update((v) => !v);
  }

  logout(): void {
    this.authService.logout();
  }
}
