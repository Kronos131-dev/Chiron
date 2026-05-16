import { Component, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { ChironApi } from '../../service/chiron-api';
import { AuthService } from '../../service/auth.service';
import { HeaderComponent } from '../shared/header/header';

@Component({
  selector: 'app-settings',
  standalone: true,
  imports: [CommonModule, FormsModule, HeaderComponent],
  templateUrl: './settings.html',
})
export class Settings {
  username: string;

  // Section ouverte
  openSection = signal<'password' | 'email' | 'username' | 'delete' | null>(null);

  // Champs password
  currentPasswordForPwd = '';
  newPassword = '';
  confirmPassword = '';

  // Champs email
  currentPasswordForEmail = '';
  newEmail = '';

  // Champs username
  currentPasswordForUsername = '';
  newUsername = '';

  // Suppression
  deleteConfirm = '';

  // Feedback
  successMessage = signal('');
  errorMessage = signal('');
  isLoading = signal(false);

  constructor(
    private chironApi: ChironApi,
    private authService: AuthService,
    private router: Router
  ) {
    this.username = this.authService.getUsername() || '';
    this.newUsername = this.username;
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
    this.chironApi.changeEmail({ currentPassword: this.currentPasswordForEmail, newEmail: this.newEmail }).subscribe({
      next: () => {
        this.isLoading.set(false);
        this.successMessage.set('Email mis à jour.');
        this.currentPasswordForEmail = '';
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
    this.chironApi.changeUsername({ currentPassword: this.currentPasswordForUsername, newUsername: this.newUsername }).subscribe({
      next: (res) => {
        this.isLoading.set(false);
        // Mise à jour du token sans déconnexion
        this.authService.saveToken(res.token);
        this.username = this.newUsername;
        this.successMessage.set('Pseudo mis à jour.');
        this.currentPasswordForUsername = '';
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
}
