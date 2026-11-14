import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthService } from '../../../service/auth.service';
import { ChironApi } from '../../../service/chiron-api';
import { HeaderComponent } from './header';

describe('HeaderComponent', () => {
  let fixture: ComponentFixture<HeaderComponent>;
  let composant: HeaderComponent;

  beforeEach(() => {
    localStorage.setItem('chiron_lang', 'fr');
    TestBed.configureTestingModule({
      imports: [HeaderComponent],
      providers: [
        { provide: ChironApi, useValue: { getProfile: vi.fn().mockReturnValue(of({})) } },
        { provide: Router, useValue: { navigate: vi.fn() } },
        { provide: AuthService, useValue: { getUsername: () => 'alice', logout: vi.fn() } },
      ],
    });
    fixture = TestBed.createComponent(HeaderComponent);
    composant = fixture.componentInstance;
    fixture.detectChanges();
  });

  function lien(adresse: string): HTMLAnchorElement | null {
    return (fixture.nativeElement as HTMLElement).querySelector<HTMLAnchorElement>(
      `a[href="${adresse}"]`,
    );
  }

  it('ne montre pas le menu tant qu il est fermé', () => {
    expect(lien('/exports')).toBeNull();
  });

  it('propose « Mes exports » dans le menu ouvert', () => {
    composant.toggleSettings();
    fixture.detectChanges();

    expect(lien('/exports')?.textContent).toContain('Mes exports');
  });

  it('place « Mes exports » juste après « Mes Annales »', () => {
    composant.toggleSettings();
    fixture.detectChanges();

    const adresses = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('a[href^="/"]'),
    ).map((a) => a.getAttribute('href'));

    expect(adresses.indexOf('/exports')).toBe(adresses.indexOf('/journal') + 1);
  });
});
