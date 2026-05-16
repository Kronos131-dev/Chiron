import { Component, signal, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { ChironApi } from '../../service/chiron-api';

@Component({
  selector: 'app-reset-password',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './reset-password.html',
})
export class ResetPassword implements OnInit {
  token = '';
  newPassword = '';
  confirmPassword = '';

  isLoading = signal(false);
  successMessage = signal('');
  errorMessage = signal('');

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private chironApi: ChironApi
  ) {}

  ngOnInit() {
    this.token = this.route.snapshot.queryParamMap.get('token') || '';
    if (!this.token) {
      this.errorMessage.set('Lien de réinitialisation invalide ou manquant.');
    }
  }

  onSubmit() {
    if (this.newPassword !== this.confirmPassword) {
      this.errorMessage.set('Les mots de passe ne correspondent pas.');
      return;
    }
    this.isLoading.set(true);
    this.errorMessage.set('');
    this.chironApi.resetPassword(this.token, this.newPassword).subscribe({
      next: () => {
        this.isLoading.set(false);
        this.successMessage.set('Mot de passe réinitialisé. Redirection...');
        setTimeout(() => this.router.navigate(['/login']), 2000);
      },
      error: (err) => {
        this.isLoading.set(false);
        this.errorMessage.set(err.error?.message || 'Lien invalide ou expiré.');
      }
    });
  }
}
