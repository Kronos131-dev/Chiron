import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthService } from '../../service/auth.service';
import { ChironApi, ExportProgramme } from '../../service/chiron-api';
import { TelechargementExport } from '../../service/telechargement-export';
import { Exports } from './exports';

function classeur(surcharge: Partial<ExportProgramme> = {}): ExportProgramme {
  return {
    id: 9,
    nom: 'Programme force',
    creeA: '2026-11-02T10:00:00',
    modeles: [
      { id: 5, titre: 'Push A' },
      { id: 6, titre: 'Pull A' },
    ],
    seances: [],
    seancesExclues: [],
    nbSeances: 4,
    ...surcharge,
  };
}

describe('Exports', () => {
  let fixture: ComponentFixture<Exports>;
  let composant: Exports;
  const router = { navigate: vi.fn() };
  const telechargementExport = { telecharger: vi.fn() };
  let chironApi: {
    getExports: ReturnType<typeof vi.fn>;
    supprimerExport: ReturnType<typeof vi.fn>;
    getProfile: ReturnType<typeof vi.fn>;
  };

  async function monter(exports: ExportProgramme[] = [classeur()]) {
    chironApi.getExports.mockReturnValue(of(exports));
    fixture = TestBed.createComponent(Exports);
    composant = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  function texte(): string {
    return (fixture.nativeElement as HTMLElement).textContent ?? '';
  }

  function bouton(libelle: string): HTMLButtonElement {
    const boutons = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll<HTMLButtonElement>('article button'),
    );
    const trouve = boutons.find((b) => b.textContent?.trim() === libelle);
    if (!trouve) throw new Error(`bouton introuvable : ${libelle}`);
    return trouve;
  }

  beforeEach(() => {
    localStorage.setItem('chiron_lang', 'fr');
    router.navigate.mockReset();
    telechargementExport.telecharger.mockReset().mockResolvedValue(undefined);
    chironApi = {
      getExports: vi.fn(),
      supprimerExport: vi.fn(),
      getProfile: vi.fn().mockReturnValue(of({ isAdmin: false })),
    };
    TestBed.configureTestingModule({
      imports: [Exports],
      providers: [
        { provide: ChironApi, useValue: chironApi },
        { provide: Router, useValue: router },
        { provide: AuthService, useValue: { getUsername: () => 'alice' } },
        { provide: TelechargementExport, useValue: telechargementExport },
      ],
    });
  });

  afterEach(() => vi.restoreAllMocks());

  it('liste les exports avec leurs onglets, leur nombre de séances et leur date', async () => {
    await monter();

    expect(chironApi.getExports).toHaveBeenCalledTimes(1);
    expect(texte()).toContain('Programme force');
    expect(texte()).toContain('Push A');
    expect(texte()).toContain('Pull A');
    expect(texte()).toContain('4 séances');
    expect(texte()).toContain('créé le 2 novembre 2026');
  });

  it('écrit "1 séance" au singulier', async () => {
    await monter([classeur({ nbSeances: 1 })]);

    expect(texte()).toContain('1 séance');
    expect(texte()).not.toContain('1 séances');
  });

  it('invite à créer un premier export quand il n y en a aucun', async () => {
    await monter([]);

    expect(texte()).toContain('Aucun export pour l');
  });

  it('affiche une erreur quand le chargement échoue', async () => {
    chironApi.getExports.mockReturnValue(throwError(() => new Error('500')));
    fixture = TestBed.createComponent(Exports);
    composant = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(composant.erreur()).toBe('Impossible de charger tes exports.');
    expect(composant.chargement()).toBe(false);
    expect(texte()).toContain('Impossible de charger tes exports.');
  });

  it('ouvre la page de création', async () => {
    await monter();

    composant.nouvelExport();

    expect(router.navigate).toHaveBeenCalledWith(['/exports/new']);
  });

  it('ouvre l assistant en mode édition sur l export choisi', async () => {
    await monter();

    bouton('Modifier').click();

    expect(router.navigate).toHaveBeenCalledWith(['/exports', 9, 'edit']);
  });

  it('télécharge le fichier', async () => {
    await monter();

    bouton('Télécharger').click();
    await fixture.whenStable();

    expect(telechargementExport.telecharger).toHaveBeenCalledWith(
      expect.objectContaining({ id: 9 }),
    );
    expect(composant.occupe()).toBeNull();
  });

  it('affiche une erreur et se libère quand le téléchargement échoue', async () => {
    telechargementExport.telecharger.mockRejectedValue(new Error('503'));
    await monter();

    await composant.telecharger(classeur());

    expect(composant.erreur()).toBe('Impossible de télécharger le fichier.');
    expect(composant.occupe()).toBeNull();
  });

  it('ignore un second téléchargement pendant qu un autre est en cours', async () => {
    await monter();
    composant.occupe.set(3);

    await composant.telecharger(classeur());

    expect(telechargementExport.telecharger).not.toHaveBeenCalled();
  });

  it('supprime l export après confirmation', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    chironApi.supprimerExport.mockReturnValue(of(undefined));
    await monter();

    composant.supprimer(classeur());

    expect(chironApi.supprimerExport).toHaveBeenCalledWith(9);
    expect(composant.exports()).toEqual([]);
  });

  it('garde l export quand la confirmation est refusée', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(false);
    await monter();

    composant.supprimer(classeur());

    expect(chironApi.supprimerExport).not.toHaveBeenCalled();
    expect(composant.exports()).toHaveLength(1);
  });

  it('garde l export quand la suppression échoue', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    chironApi.supprimerExport.mockReturnValue(throwError(() => new Error('500')));
    await monter();

    composant.supprimer(classeur());

    expect(composant.exports()).toHaveLength(1);
    expect(composant.erreur()).toBe("Impossible de supprimer l'export.");
    expect(composant.occupe()).toBeNull();
  });
});
