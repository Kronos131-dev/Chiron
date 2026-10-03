import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { environment } from '../../environments/environment';
import { AuthService } from '../service/auth.service';
import { authInterceptor } from './auth.interceptor';

const CLE = 'chiron_jwt';

function jeton(exp: number): string {
  return `e30.${btoa(JSON.stringify({ sub: 'Kronos', exp })).replace(/=+$/, '')}.signature`;
}

const valable = () => jeton(Math.floor(Date.now() / 1000) + 3600);
const perime = () => jeton(Math.floor(Date.now() / 1000) - 3600);

describe('authInterceptor', () => {
  let http: HttpClient;
  let controle: HttpTestingController;
  let logout: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        { provide: Router, useValue: { navigate: vi.fn() } },
      ],
    });
    http = TestBed.inject(HttpClient);
    controle = TestBed.inject(HttpTestingController);
    logout = vi.spyOn(TestBed.inject(AuthService), 'logout');
  });

  afterEach(() => localStorage.clear());

  function appeler(chemin: string, statut: number) {
    http.get(`${environment.apiUrl}${chemin}`).subscribe({ error: () => undefined });
    controle
      .expectOne(`${environment.apiUrl}${chemin}`)
      .flush('', { status: statut, statusText: 'x' });
  }

  it('joint le jeton aux appels de l API', () => {
    const token = valable();
    localStorage.setItem(CLE, token);
    http.get(`${environment.apiUrl}/journal`).subscribe();
    const requete = controle.expectOne(`${environment.apiUrl}/journal`);
    expect(requete.request.headers.get('Authorization')).toBe(`Bearer ${token}`);
    requete.flush({});
  });

  it('déconnecte et renvoie vers la connexion sur un 401', () => {
    localStorage.setItem(CLE, valable());
    appeler('/journal', 401);
    expect(logout).toHaveBeenCalledTimes(1);
  });

  it('déconnecte sur un 403 quand le jeton est expiré', () => {
    const token = perime();
    localStorage.setItem(CLE, token);
    appeler('/journal', 403);
    expect(logout).toHaveBeenCalledTimes(1);
  });

  it('laisse l écran gérer un 403 quand le jeton est encore valable', () => {
    localStorage.setItem(CLE, valable());
    appeler('/profile/alice', 403);
    expect(logout).not.toHaveBeenCalled();
  });

  it('ne déconnecte pas sur les routes d authentification', () => {
    localStorage.setItem(CLE, perime());
    appeler('/auth/authenticate', 403);
    expect(logout).not.toHaveBeenCalled();
  });

  it('ne déconnecte pas sur une autre erreur', () => {
    localStorage.setItem(CLE, valable());
    appeler('/journal', 500);
    expect(logout).not.toHaveBeenCalled();
  });

  it('ne touche pas aux appels vers d autres hôtes', () => {
    localStorage.setItem(CLE, perime());
    http.get('https://exemple.test/x').subscribe({ error: () => undefined });
    controle.expectOne('https://exemple.test/x').flush('', { status: 401, statusText: 'x' });
    expect(logout).not.toHaveBeenCalled();
  });

  it('propage l erreur à l appelant', () => {
    localStorage.setItem(CLE, valable());
    const erreur = vi.fn();
    http.get(`${environment.apiUrl}/journal`).subscribe({ error: erreur });
    controle.expectOne(`${environment.apiUrl}/journal`).flush('', { status: 401, statusText: 'x' });
    expect(erreur).toHaveBeenCalled();
  });
});
