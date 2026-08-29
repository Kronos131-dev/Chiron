import { Component, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule, FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../service/auth.service';
import { ChironApi } from '../../service/chiron-api';
import { I18nService } from '../../service/i18n.service';
import { TranslatePipe } from '../../service/translate.pipe';
import { catchError, EMPTY, timeout } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';

/**
 * Component handling user authentication and registration.
 * Provides a unified form that toggles between login and sign-up modes.
 */
const DELAI_MAX_MS = 20000;

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

  // WHY: l'application tourne sans zone.js. Un champ simple modifié dans un callback HTTP ne
  // redéclenche aucun rendu : sur un login en échec, le message restait invisible et le
  // bouton tournait indéfiniment. Seul un signal réveille la détection de changement.
  readonly errorMessage = signal('');
  readonly isLoading = signal(false);
  readonly isLoginMode = signal(true);
  readonly isForgotMode = signal(false);
  readonly forgotEmail = signal('');
  readonly forgotMessage = signal('');

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
    this.isLoginMode.update((mode) => !mode);
    this.isForgotMode.set(false);
    this.errorMessage.set('');
    this.forgotMessage.set('');
  }

  showForgot() {
    this.isForgotMode.set(true);
    this.isLoginMode.set(true);
    this.errorMessage.set('');
    this.forgotMessage.set('');
  }

  hideForgot() {
    this.isForgotMode.set(false);
    this.forgotMessage.set('');
    this.forgotEmail.set('');
  }

  onForgotPassword() {
    if (!this.forgotEmail()) return;
    this.isLoading.set(true);
    this.chironApi.forgotPassword(this.forgotEmail()).pipe(
      timeout(DELAI_MAX_MS),
      catchError(() => {
        this.isLoading.set(false);
        this.forgotMessage.set(this.i18n.t('login.forgotSent'));
        return EMPTY;
      })
    ).subscribe(() => {
      this.isLoading.set(false);
      this.forgotMessage.set(this.i18n.t('login.forgotSent'));
    });
  }

  /**
   * Handles form submission.
   * Validates inputs, triggers the appropriate authentication service method,
   * and manages loading states and error handling.
   */
  onSubmit(): void {
    if (this.loginForm.invalid) return;

    if (!this.isLoginMode() && !this.loginForm.value.email) {
      this.errorMessage.set(this.i18n.t('login.emailRequired'));
      return;
    }

    if (!this.isLoginMode() && this.loginForm.value.password !== this.loginForm.value.confirmPassword) {
      this.errorMessage.set(this.i18n.t('login.passwordMismatch'));
      return;
    }

    this.isLoading.set(true);
    this.errorMessage.set('');

    const authObservable = this.isLoginMode()
      ? this.authService.login(this.loginForm.value)
      : this.authService.register(this.loginForm.value);

    // WHY: une XHR n'a pas de délai maximum. Serveur injoignable qui accepte la connexion sans
    // répondre, et la requête pend pour toujours — c'est ce que l'athlète voit comme un bouton
    // qui tourne sans fin. Le délai transforme l'attente en message.
    authObservable.pipe(timeout(DELAI_MAX_MS)).subscribe({
      next: () => {
        this.isLoading.set(false);
        this.router.navigate(['/chat']);
      },
      error: (err) => {
        this.isLoading.set(false);
        this.errorMessage.set(this.messageDErreur(err));
      }
    });
  }

  private messageDErreur(erreur: unknown): string {
    if (!(erreur instanceof HttpErrorResponse)) return this.i18n.t('login.networkError');
    if (erreur.status === 0) return this.i18n.t('login.networkError');
    if (!this.isLoginMode()) return this.i18n.t('login.registerError');
    if (erreur.status === 401 || erreur.status === 403) return this.i18n.t('login.badCreds');
    return this.i18n.t('login.serverError', { statut: String(erreur.status) });
  }
}
