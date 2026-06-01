import { Component, OnInit, OnDestroy, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { ChironApi, NutritionLinkStatus, FitbitLinkStatus } from '../../service/chiron-api';
import { AuthService } from '../../service/auth.service';
import { HeaderComponent } from '../shared/header/header';

@Component({
  selector: 'app-settings',
  standalone: true,
  imports: [CommonModule, FormsModule, HeaderComponent],
  templateUrl: './settings.html',
})
export class Settings implements OnInit, OnDestroy {
  username: string;
  currentEmail = signal<string | null>(null);

  openSection = signal<'password' | 'email' | 'username' | 'delete' | null>(null);

  // Champs password
  currentPasswordForPwd = '';
  newPassword = '';
  confirmPassword = '';

  // Champs email / username
  newEmail = '';
  newUsername = '';

  // Suppression
  deleteConfirm = '';

  successMessage = signal('');
  errorMessage = signal('');
  isLoading = signal(false);

  // --- Liaison Olympus ---
  nutritionStatus = signal<NutritionLinkStatus | null>(null);
  olympusPseudo = signal('');
  olympusPassword = signal('');
  isLinking = signal(false);
  linkError = signal<string | null>(null);

  // --- Liaison Fitbit ---
  fitbitStatus = signal<FitbitLinkStatus | null>(null);
  isFitbitConnecting = signal(false);
  fitbitError = signal<string | null>(null);
  private fitbitPollHandle: ReturnType<typeof setInterval> | null = null;

  constructor(
    private chironApi: ChironApi,
    private authService: AuthService,
    private router: Router
  ) {
    this.username = this.authService.getUsername() || '';
    this.newUsername = this.username;
    this.chironApi.getSettingsInfo().subscribe({
      next: (info) => {
        this.currentEmail.set(info.email);
        this.newEmail = info.email ?? '';
      }
    });
  }

  ngOnInit() {
    this.loadNutritionStatus();
    this.loadFitbitStatus();
  }

  ngOnDestroy() {
    this.stopFitbitPolling();
  }

  toggle(section: 'password' | 'email' | 'username' | 'delete') {
    this.openSection.set(this.openSection() === section ? null : section);
    this.clearFeedback();
  }

  private clearFeedback() {
    this.successMessage.set('');
    this.errorMessage.set('');
  }

  onChangePassword() {
    if (this.newPassword !== this.confirmPassword) {
      this.errorMessage.set('Les mots de passe ne correspondent pas.');
      return;
    }
    this.isLoading.set(true);
    this.clearFeedback();
    this.chironApi.changePassword({ currentPassword: this.currentPasswordForPwd, newPassword: this.newPassword }).subscribe({
      next: () => {
        this.isLoading.set(false);
        this.successMessage.set('Mot de passe mis à jour.');
        this.currentPasswordForPwd = '';
        this.newPassword = '';
        this.confirmPassword = '';
      },
      error: (err) => {
        this.isLoading.set(false);
        this.errorMessage.set(err.error?.message || 'Mot de passe actuel incorrect.');
      }
    });
  }

  onChangeEmail() {
    this.isLoading.set(true);
    this.clearFeedback();
    this.chironApi.changeEmail({ newEmail: this.newEmail }).subscribe({
      next: () => {
        this.isLoading.set(false);
        this.currentEmail.set(this.newEmail);
        this.successMessage.set('Email mis à jour.');
      },
      error: (err) => {
        this.isLoading.set(false);
        this.errorMessage.set(err.error?.message || 'Erreur lors du changement d\'email.');
      }
    });
  }

  onChangeUsername() {
    this.isLoading.set(true);
    this.clearFeedback();
    this.chironApi.changeUsername({ newUsername: this.newUsername }).subscribe({
      next: (res) => {
        this.isLoading.set(false);
        this.authService.saveToken(res.token);
        this.username = this.newUsername;
        this.successMessage.set('Pseudo mis à jour.');
      },
      error: (err) => {
        this.isLoading.set(false);
        this.errorMessage.set(err.error?.message || 'Erreur lors du changement de pseudo.');
      }
    });
  }

  onDeleteAccount() {
    if (this.deleteConfirm !== this.username) {
      this.errorMessage.set('Saisie incorrecte. Tape exactement ton pseudo pour confirmer.');
      return;
    }
    this.isLoading.set(true);
    this.chironApi.deleteProfile(this.username).subscribe({
      next: () => {
        this.authService.logout();
      },
      error: () => {
        this.isLoading.set(false);
        this.errorMessage.set('Erreur lors de la suppression du compte.');
      }
    });
  }

  // --- Profil sportif ---

  goToOnboarding() {
    this.router.navigate(['/onboarding']);
  }

  // --- Liaison Olympus ---

  loadNutritionStatus() {
    this.chironApi.getNutritionStatus().subscribe({
      next: (status) => this.nutritionStatus.set(status),
      error: () => this.nutritionStatus.set(null)
    });
  }

  linkOlympus() {
    const pseudo = this.olympusPseudo().trim();
    const password = this.olympusPassword();
    if (!pseudo || !password) {
      this.linkError.set('Pseudo et mot de passe requis.');
      return;
    }
    this.isLinking.set(true);
    this.linkError.set(null);
    this.chironApi.linkOlympus(pseudo, password).subscribe({
      next: (status) => {
        this.nutritionStatus.set(status);
        this.olympusPseudo.set('');
        this.olympusPassword.set('');
        this.isLinking.set(false);
        this.successMessage.set('Compte Olympus lié. Ton tableau de bord nutrition est maintenant disponible.');
      },
      error: (err) => {
        this.isLinking.set(false);
        const msg = err?.error?.message ?? 'Échec de la liaison.';
        this.linkError.set(msg);
      }
    });
  }

  unlinkOlympus() {
    if (!confirm('Délier votre compte Olympus de Chiron ?')) return;
    this.chironApi.unlinkOlympus().subscribe({
      next: () => this.loadNutritionStatus(),
      error: () => alert('Erreur lors du déliage.')
    });
  }

  // --- Liaison Fitbit ---

  loadFitbitStatus() {
    this.chironApi.getFitbitStatus().subscribe({
      next: (status) => this.fitbitStatus.set(status),
      error: () => this.fitbitStatus.set(null)
    });
  }

  connectFitbit() {
    this.fitbitError.set(null);
    this.isFitbitConnecting.set(true);
    this.chironApi.getFitbitAuthorizeUrl().subscribe({
      next: ({ authorizeUrl }) => {
        window.open(authorizeUrl, '_blank');
        this.startFitbitPolling();
      },
      error: () => {
        this.isFitbitConnecting.set(false);
        this.fitbitError.set('Impossible de démarrer la connexion Fitbit.');
      }
    });
  }

  disconnectFitbit() {
    if (!confirm('Déconnecter ton compte Fitbit de Chiron ?')) return;
    this.chironApi.unlinkFitbit().subscribe({
      next: () => this.loadFitbitStatus(),
      error: () => alert('Erreur lors de la déconnexion.')
    });
  }

  goToFitbitDashboard() {
    this.router.navigate(['/fitbit']);
  }

  private startFitbitPolling() {
    this.stopFitbitPolling();
    let elapsedSeconds = 0;
    this.fitbitPollHandle = setInterval(() => {
      elapsedSeconds += 3;
      this.chironApi.getFitbitStatus().subscribe({
        next: (status) => {
          if (status.linked) {
            this.fitbitStatus.set(status);
            this.isFitbitConnecting.set(false);
            this.stopFitbitPolling();
          }
        },
        error: () => {}
      });
      if (elapsedSeconds >= 180) {
        this.isFitbitConnecting.set(false);
        this.stopFitbitPolling();
      }
    }, 3000);
  }

  private stopFitbitPolling() {
    if (this.fitbitPollHandle != null) {
      clearInterval(this.fitbitPollHandle);
      this.fitbitPollHandle = null;
    }
  }
}
