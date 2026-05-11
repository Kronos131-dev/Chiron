import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../service/auth.service';

/**
 * Component handling user authentication and registration.
 * Provides a unified form that toggles between login and sign-up modes.
 */
@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
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

  /**
   * Initializes the Login component and configures the reactive form validation.
   *
   * @param fb          FormBuilder to construct the reactive form.
   * @param authService Service handling authentication API calls.
   * @param router      Router to navigate upon successful authentication.
   */
  constructor(
    private fb: FormBuilder,
    private authService: AuthService,
    private router: Router
  ) {
    this.loginForm = this.fb.group({
      username: ['', Validators.required],
      email: [''],
      password: ['', Validators.required]
    });
  }

  /**
   * Toggles the component state between Login and Registration modes.
   * Clears any existing error messages upon switching.
   */
  switchMode() {
    this.isLoginMode = !this.isLoginMode;
    this.errorMessage = '';
  }

  /**
   * Handles form submission.
   * Validates inputs, triggers the appropriate authentication service method,
   * and manages loading states and error handling.
   */
  onSubmit(): void {
    if (this.loginForm.invalid) return;

    if (!this.isLoginMode && !this.loginForm.value.email) {
      this.errorMessage = "L'email est requis pour s'inscrire.";
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
          ? 'Identifiants incorrects ou serveur injoignable.'
          : 'Erreur lors de la création du compte (nom d\'utilisateur peut-être déjà pris).';
        console.error("Erreur d'authentification :", err);
      }
    });
  }
}
