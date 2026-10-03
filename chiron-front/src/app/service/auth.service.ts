import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { Router } from '@angular/router';
import { environment } from '../../environments/environment';

interface ContenuJeton {
  sub?: string;
  exp?: number;
}

const MS_PAR_SECONDE = 1000;

function lireContenu(jeton: string): ContenuJeton | null {
  try {
    const base64 = (jeton.split('.')[1] ?? '').replace(/-/g, '+').replace(/_/g, '/');
    const octets = atob(base64)
      .split('')
      .map((caractere) => '%' + caractere.charCodeAt(0).toString(16).padStart(2, '0'))
      .join('');
    return JSON.parse(decodeURIComponent(octets));
  } catch {
    return null;
  }
}

/**
 * Service responsible for handling user authentication and token management.
 * Provides methods for user registration, login, and managing the JWT session token.
 */
@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private apiUrl = `${environment.apiUrl}/auth`;
  private tokenKey = 'chiron_jwt';

  /**
   * Initializes a new instance of the AuthService.
   *
   * @param http   The Angular HttpClient for making HTTP requests.
   * @param router The Angular Router for navigating between application routes.
   */
  constructor(private http: HttpClient, private router: Router) {}

  /**
   * Registers a new user with the provided data.
   * Automatically saves the resulting authentication token upon successful registration.
   *
   * @param data The user registration details.
   * @returns An Observable emitting the HTTP response from the backend.
   */
  register(data: any): Observable<any> {
    return this.http.post(`${this.apiUrl}/register`, data).pipe(
      tap((response: any) => this.saveToken(response.token))
    );
  }

  /**
   * Authenticates a user with the provided credentials.
   * Automatically saves the resulting authentication token upon successful login.
   *
   * @param credentials The user's login credentials (e.g., username and password).
   * @returns An Observable emitting the HTTP response containing the authentication token.
   */
  login(credentials: any): Observable<any> {
    return this.http.post(`${this.apiUrl}/authenticate`, credentials).pipe(
      tap((response: any) => this.saveToken(response.token))
    );
  }

  /**
   * Persists the provided JSON Web Token (JWT) in local storage.
   *
   * @param token The JWT string to be saved.
   */
  saveToken(token: string): void {
    localStorage.setItem(this.tokenKey, token);
  }

  /**
   * Retrieves the current JSON Web Token (JWT) from local storage.
   *
   * @returns The JWT string if it exists, or null otherwise.
   */
  getToken(): string | null {
    return localStorage.getItem(this.tokenKey);
  }

  /**
   * Checks whether the user is currently logged in by verifying the presence of a token.
   *
   * @returns True if the user has an authentication token; false otherwise.
   */
  isLoggedIn(): boolean {
    const jeton = this.getToken();
    if (!jeton) return false;
    if (this.jetonPerime(jeton)) {
      localStorage.removeItem(this.tokenKey);
      return false;
    }
    return true;
  }

  jetonPerime(jeton: string | null = this.getToken()): boolean {
    if (!jeton) return true;
    const contenu = lireContenu(jeton);
    if (!contenu) return true;
    return contenu.exp !== undefined && contenu.exp * MS_PAR_SECONDE <= Date.now();
  }

  /**
   * Decodes the payload of the stored JWT to extract the user's username.
   *
   * @returns The username embedded in the token's subject claim, or null if no token is found.
   */
  getUsername(): string | null {
    const token = localStorage.getItem('chiron_jwt');
    if (!token) return null;
    return lireContenu(token)?.sub ?? null;
  }

  /**
   * Logs out the current user by removing their token from local storage
   * and navigating them back to the login page.
   */
  logout(): void {
    localStorage.removeItem('chiron_jwt');
    this.router.navigate(['/login']);
  }
}
