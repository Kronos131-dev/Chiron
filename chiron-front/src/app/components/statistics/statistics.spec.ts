import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthService } from '../../service/auth.service';
import { ChironApi } from '../../service/chiron-api';
import { Statistics } from './statistics';

describe('Statistics', () => {
  let fixture: ComponentFixture<Statistics>;
  let composant: Statistics;
  const router = { navigate: vi.fn() };
  let chironApi: {
    getStatsOverview: ReturnType<typeof vi.fn>;
    getPerformanceSummary: ReturnType<typeof vi.fn>;
    getProfile: ReturnType<typeof vi.fn>;
  };

  async function monter() {
    fixture = TestBed.createComponent(Statistics);
    composant = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  function boutonExport(): HTMLButtonElement | undefined {
    return Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll<HTMLButtonElement>('main button'),
    ).find((bouton) => bouton.textContent?.includes('Exporter ma progression'));
  }

  beforeEach(() => {
    localStorage.setItem('chiron_lang', 'fr');
    router.navigate.mockReset();
    chironApi = {
      getStatsOverview: vi.fn().mockReturnValue(
        of({
          seances30: 12,
          seancesTotal: 140,
          tonnageSemaine: 8200,
          streakSemaines: 5,
          poidsCorps: 78,
        }),
      ),
      getPerformanceSummary: vi.fn().mockReturnValue(of({ exercises: [] })),
      getProfile: vi.fn().mockReturnValue(of({ isAdmin: false })),
    };
    TestBed.configureTestingModule({
      imports: [Statistics],
      providers: [
        { provide: ChironApi, useValue: chironApi },
        { provide: Router, useValue: router },
        { provide: AuthService, useValue: { getUsername: () => 'alice' } },
      ],
    });
  });

  it('affiche le bouton d export dès l ouverture de la page', async () => {
    await monter();

    expect(boutonExport()).toBeDefined();
    expect(boutonExport()?.type).toBe('button');
  });

  it('place le bouton avant le sélecteur de période et les onglets', async () => {
    await monter();

    const main = (fixture.nativeElement as HTMLElement).querySelector('main') as HTMLElement;
    const boutons = Array.from(main.querySelectorAll('button'));
    const indexExport = boutons.indexOf(boutonExport() as HTMLButtonElement);
    const indexPeriode = boutons.findIndex((b) => b.textContent?.includes('30'));
    const indexOnglet = boutons.findIndex((b) => b.textContent?.includes('Force'));

    expect(indexExport).toBe(0);
    expect(indexExport).toBeLessThan(indexPeriode);
    expect(indexExport).toBeLessThan(indexOnglet);
  });

  it('garde le bouton visible sur les autres onglets', async () => {
    await monter();

    composant.selectTab('force');
    fixture.detectChanges();

    expect(composant.activeTab()).toBe('force');
    expect(boutonExport()).toBeDefined();
  });

  it('garde le bouton visible après un changement de période', async () => {
    await monter();

    composant.changePeriode(90);
    fixture.detectChanges();

    expect(boutonExport()).toBeDefined();
  });

  it('ouvre la liste des exports au clic', async () => {
    await monter();

    boutonExport()?.click();

    expect(router.navigate).toHaveBeenCalledWith(['/exports']);
  });
});
