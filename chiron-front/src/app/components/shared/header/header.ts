import { Component, Input, OnInit, signal, HostListener } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { AuthService } from '../../../service/auth.service';
import { ChironApi } from '../../../service/chiron-api';
import { environment } from '../../../../environments/environment';

@Component({
  selector: 'app-header',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './header.html',
})
export class HeaderComponent implements OnInit {
  @Input() title: string = 'CHIRON';
  @Input() showBack: boolean = false;
  @Input() backRoute: string = '/chat';

  showSettings = signal(false);
  currentUsername: string = '';
  isAdmin = signal(false);

  constructor(
    private authService: AuthService,
    private router: Router,
    private chironApi: ChironApi
  ) {}

  ngOnInit() {
    this.currentUsername = this.authService.getUsername() || 'Guerrier';
    if (this.currentUsername !== 'Guerrier') {
      this.chironApi.getProfile(this.currentUsername, this.currentUsername).subscribe({
        next: (profile) => {
          if (profile && profile.isAdmin) {
            this.isAdmin.set(true);
          }
        },
        error: () => console.log("Erreur lors de la récupération du profil pour vérifier les droits admin")
      });
    }
  }

  toggleSettings() {
    this.showSettings.update(v => !v);
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent) {
    const target = event.target as HTMLElement;
    if (!target.closest('app-header')) {
      this.showSettings.set(false);
    }
  }

  /**
   * Ouvre la PWA Olympus (sous-domaine dédié). Si le compte est lié, on récupère le token
   * de liaison et on lance Olympus avec l'« entrée directe » (#ctk=…) — la PWA ouvre alors
   * une session sans nouvelle connexion. Sinon, on ouvre Olympus sur sa page de connexion.
   */
  openOlympus() {
    this.showSettings.set(false);
    // Le fragment #ctk est lu par la PWA Olympus puis retiré de l'URL.
    this.chironApi.getOlympusHandoff().subscribe({
      next: ({ token }) => {
        window.location.href = `${environment.olympusUrl}#ctk=${encodeURIComponent(token)}`;
      },
      error: () => {
        // Compte non lié (409) ou Olympus indisponible : on laisse la PWA gérer le login.
        window.location.href = environment.olympusUrl;
      },
    });
  }

  logout() {
    this.authService.logout();
  }

  goBack() {
    this.router.navigate([this.backRoute]);
  }
}
