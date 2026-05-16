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
  styleUrls: ['./reset-password.css'],
})
export class ResetPassword implements OnInit {
  token = '';
  newPassword = '';
  confirmPassword = '';

  showNew = false;
  showConfirm = false;

  isLoading = signal(false);
  success = signal(false);
  errorMessage = signal('');

  constructor(
    private route: ActivatedRoute,
    public router: Router,
    private chironApi: ChironApi
  ) {}

  ngOnInit() {
    this.token = this.route.snapshot.queryParamMap.get('token') || '';
    if (!this.token) {
      this.errorMessage.set('Lien de réinitialisation invalide ou manquant.');
    }
  }

  get strength(): number {
    const p = this.newPassword;
    if (!p) return 0;
    let score = 0;
    if (p.length >= 8) score++;
    if (/[A-Z]/.test(p)) score++;
    if (/[0-9]/.test(p)) score++;
    if (/[^A-Za-z0-9]/.test(p)) score++;
    return score;
  }

  get strengthColor(): string {
    return ['', '#ef4444', '#f97316', '#eab308', '#22c55e'][this.strength] || '';
  }

  get strengthWidth(): string {
    return ['0%', '25%', '50%', '75%', '100%'][this.strength];
  }

  get passwordsMatch(): boolean {
    return !!this.confirmPassword && this.newPassword === this.confirmPassword;
  }

  get canSubmit(): boolean {
    return !!this.token && this.newPassword.length >= 6 && this.passwordsMatch;
  }

  onSubmit() {
    if (!this.canSubmit) return;
    this.isLoading.set(true);
    this.errorMessage.set('');
    this.chironApi.resetPassword(this.token, this.newPassword).subscribe({
      next: () => {
        this.isLoading.set(false);
        this.success.set(true);
        setTimeout(() => this.router.navigate(['/login']), 3000);
      },
      error: (err) => {
        this.isLoading.set(false);
        this.errorMessage.set(err.error?.message || 'Lien invalide ou expiré. Demande un nouveau lien.');
      }
    });
  }
}
