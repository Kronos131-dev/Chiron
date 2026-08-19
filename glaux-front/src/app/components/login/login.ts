import { Component, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../service/auth.service';
import { TranslatePipe } from '../../service/translate.pipe';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslatePipe],
  templateUrl: './login.html',
  styleUrl: './login.css',
})
export class Login {
  username = signal('');
  password = signal('');
  loading = signal(false);
  error = signal(false);

  constructor(
    private authService: AuthService,
    private router: Router,
  ) {}

  submit(): void {
    if (!this.username() || !this.password()) return;
    this.loading.set(true);
    this.error.set(false);
    this.authService.login({ username: this.username(), password: this.password() }).subscribe({
      next: () => {
        this.loading.set(false);
        this.router.navigate(['/']);
      },
      error: () => {
        this.loading.set(false);
        this.error.set(true);
      },
    });
  }
}
