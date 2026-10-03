import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { Router } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthService } from './auth.service';

const CLE = 'chiron_jwt';

function jeton(contenu: object): string {
  const base64url = btoa(unescape(encodeURIComponent(JSON.stringify(contenu))))
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/, '');
  return `e30.${base64url}.signature`;
}

const dansUneHeure = () => Math.floor(Date.now() / 1000) + 3600;
const ilYAUneHeure = () => Math.floor(Date.now() / 1000) - 3600;

describe('AuthService', () => {
  let service: AuthService;
  const router = { navigate: vi.fn() };

  beforeEach(() => {
    localStorage.clear();
    router.navigate.mockReset();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: Router, useValue: router },
      ],
    });
    service = TestBed.inject(AuthService);
  });

  afterEach(() => localStorage.clear());

  it('n est pas connecté sans jeton', () => {
    expect(service.isLoggedIn()).toBe(false);
  });

  it('est connecté avec un jeton encore valable', () => {
    localStorage.setItem(CLE, jeton({ sub: 'Kronos', exp: dansUneHeure() }));
    expect(service.isLoggedIn()).toBe(true);
  });

  it('refuse un jeton expiré et le retire du stockage', () => {
    localStorage.setItem(CLE, jeton({ sub: 'Kronos', exp: ilYAUneHeure() }));
    expect(service.isLoggedIn()).toBe(false);
    expect(localStorage.getItem(CLE)).toBeNull();
  });

  it('refuse un jeton illisible', () => {
    localStorage.setItem(CLE, 'pas-un-jeton');
    expect(service.isLoggedIn()).toBe(false);
    expect(localStorage.getItem(CLE)).toBeNull();
  });

  it('accepte un jeton sans date d expiration', () => {
    localStorage.setItem(CLE, jeton({ sub: 'Kronos' }));
    expect(service.isLoggedIn()).toBe(true);
  });

  it('lit le pseudo, accents compris', () => {
    localStorage.setItem(CLE, jeton({ sub: 'Héraclès', exp: dansUneHeure() }));
    expect(service.getUsername()).toBe('Héraclès');
  });

  it('retourne null au lieu de lever une erreur sur un jeton illisible', () => {
    localStorage.setItem(CLE, 'pas-un-jeton');
    expect(service.getUsername()).toBeNull();
  });

  it('retire le jeton et renvoie vers la connexion à la déconnexion', () => {
    localStorage.setItem(CLE, jeton({ sub: 'Kronos', exp: dansUneHeure() }));
    service.logout();
    expect(localStorage.getItem(CLE)).toBeNull();
    expect(router.navigate).toHaveBeenCalledWith(['/login']);
  });
});
