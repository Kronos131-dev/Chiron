import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule, FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../service/auth.service';
import { ChironApi } from '../../service/chiron-api';
import { I18nService } from '../../service/i18n.service';
import { TranslatePipe } from '../../service/translate.pipe';
import { catchError, EMPTY } from 'rxjs';

/**
 * Component handling user authentication and registration.
 * Provides a unified form that toggles between login and sign-up modes.
 */
@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, FormsModule, TranslatePipe],
  templateUrl: './login.html',
  styleUrls: ['./login.css']
})
export class Login {
  /** Reactive form group managing the authentication fields. */
  loginForm: FormGroup;

  /** Holds any error messages generated during authentication attempts. */
  errorMessage: string = '';

  /** Indicates whether an authentication request is currently in progress. */
  isLoading: boolean = false;

  /** Determines if the UI should display the login form (true) or registration form (false). */
  isLoginMode: boolean = true;

  /** Mot de passe oublié mode */
  isForgotMode: boolean = false;
  forgotEmail: string = '';
  forgotMessage: string = '';

  constructor(
    private fb: FormBuilder,
    private authService: AuthService,
    private router: Router,
    private chironApi: ChironApi,
    public i18n: I18nService
  ) {
    this.loginForm = this.fb.group({
      username: ['', Validators.required],
      email: [''],
      password: ['', Validators.required],
      confirmPassword: ['']
    });
  }

  /**
   * Toggles the component state between Login and Registration modes.
   * Clears any existing error messages upon switching.
   */
  switchMode() {
    this.isLoginMode = !this.isLoginMode;
    this.isForgotMode = false;
    this.errorMessage = '';
    this.forgotMessage = '';
  }

  showForgot() {
    this.isForgotMode = true;
    this.isLoginMode = true;
    this.errorMessage = '';
    this.forgotMessage = '';
  }

  hideForgot() {
    this.isForgotMode = false;
    this.forgotMessage = '';
    this.forgotEmail = '';
  }

  onForgotPassword() {
    if (!this.forgotEmail) return;
    this.isLoading = true;
    this.chironApi.forgotPassword(this.forgotEmail).pipe(
      catchError(() => {
        this.isLoading = false;
        this.forgotMessage = this.i18n.t('login.forgotSent');
        return EMPTY;
      })
    ).subscribe(() => {
      this.isLoading = false;
      this.forgotMessage = this.i18n.t('login.forgotSent');
    });
  }

  /**
   * Handles form submission.
   * Validates inputs, triggers the appropriate authentication service method,
   * and manages loading states and error handling.
   */
  onSubmit(): void {
    if (this.loginForm.invalid) return;

    if (!this.isLoginMode && !this.loginForm.value.email) {
      this.errorMessage = this.i18n.t('login.emailRequired');
      return;
    }

    if (!this.isLoginMode && this.loginForm.value.password !== this.loginForm.value.confirmPassword) {
      this.errorMessage = this.i18n.t('login.passwordMismatch');
      return;
    }

    this.isLoading = true;
    this.errorMessage = '';

    const authObservable = this.isLoginMode
      ? this.authService.login(this.loginForm.value)
      : this.authService.register(this.loginForm.value);

    authObservable.subscribe({
      next: () => {
        this.isLoading = false;
        this.router.navigate(['/chat']);
      },
      error: (err) => {
        this.isLoading = false;
        this.errorMessage = this.isLoginMode
          ? this.i18n.t('login.badCreds')
          : this.i18n.t('login.registerError');
        console.error("Erreur d'authentification :", err);
      }
    });
  }
}
