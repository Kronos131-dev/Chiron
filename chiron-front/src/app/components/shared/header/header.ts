import { Component, Input, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { AuthService } from '../../../service/auth.service';
import { ChironApi } from '../../../service/chiron-api';

@Component({
  selector: 'app-header',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './header.html',
})
export class HeaderComponent implements OnInit {
  @Input() title: string = 'CHIRON';
  @Input() icon: string = 'account_balance';
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

  logout() {
    this.authService.logout();
  }

  goBack() {
    this.router.navigate([this.backRoute]);
  }
}
