import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { of, throwError } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthService } from '../../service/auth.service';
import { ChironApi, ExportProgramme } from '../../service/chiron-api';
import { TelechargementExport } from '../../service/telechargement-export';
import { ExportAssistant } from './export-assistant';

const programmes = [
  {
    id: 5,
    titre: 'Push A',
    historique: false,
    exercices: [{ series: [{}, {}] }, { series: [{}] }],
  },
  { id: 6, titre: 'Pull A', historique: false, exercices: [{ series: [{}] }] },
  { id: 7, titre: 'Legs', historique: false, exercices: [] },
];

function faite(id: number, jour: number, modeleId: number | null, extra: object = {}) {
  return {
    id,
    titre: `Séance ${id}`,
    historique: true,
    startTime: `2026-11-${String(jour).padStart(2, '0')}T10:00:00`,
    endTime: `2026-11-${String(jour).padStart(2, '0')}T11:00:00`,
    modeleId,
    exercices: [{}],
    ...extra,
  };
}

const journal = [
  faite(30, 2, 5),
  faite(31, 3, null),
  faite(32, 10, 99),
  faite(33, 11, null, { endTime: null }),
];

function exportExistant(surcharge: Partial<ExportProgramme> = {}): ExportProgramme {
  return {
    id: 9,
    nom: 'Mon export',
    creeA: '2026-11-02T10:00:00',
    modeles: [{ id: 6, titre: 'Pull A' }],
    seances: [{ seanceId: 31, modeleId: 6 }],
    nbSeances: 1,
    ...surcharge,
  };
}

describe('ExportAssistant', () => {
  let fixture: ComponentFixture<ExportAssistant>;
  let composant: ExportAssistant;
  const router = { navigate: vi.fn() };
  const telechargementExport = { telecharger: vi.fn() };
  let chironApi: {
    getProgrammes: ReturnType<typeof vi.fn>;
    getHistorique: ReturnType<typeof vi.fn>;
    getExport: ReturnType<typeof vi.fn>;
    creerExport: ReturnType<typeof vi.fn>;
    modifierExport: ReturnType<typeof vi.fn>;
    getProfile: ReturnType<typeof vi.fn>;
  };

  async function monter(id: string | null = null) {
    TestBed.overrideProvider(ActivatedRoute, {
      useValue: { snapshot: { paramMap: convertToParamMap(id ? { id } : {}) } },
    });
    fixture = TestBed.createComponent(ExportAssistant);
    composant = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  function texte(): string {
    return (fixture.nativeElement as HTMLElement).textContent ?? '';
  }

  function cocherRoutines(...ids: number[]) {
    const routines = composant.routines().filter((routine) => ids.includes(routine.id));
    routines.forEach((routine) => composant.basculerRoutine(routine));
  }

  function faiteNumero(id: number) {
    const trouvee = composant.journal().find((seance) => seance.id === id);
    if (!trouvee) throw new Error(`séance ${id} absente du journal`);
    return trouvee;
  }

  beforeEach(() => {
    localStorage.setItem('chiron_lang', 'fr');
    router.navigate.mockReset();
    telechargementExport.telecharger.mockReset().mockResolvedValue(undefined);
    chironApi = {
      getProgrammes: vi.fn().mockReturnValue(of(programmes)),
      getHistorique: vi.fn().mockReturnValue(of(journal)),
      getExport: vi.fn().mockReturnValue(of(exportExistant())),
      creerExport: vi.fn().mockReturnValue(of(exportExistant({ id: 12 }))),
      modifierExport: vi.fn().mockReturnValue(of(exportExistant())),
      getProfile: vi.fn().mockReturnValue(of({ isAdmin: false })),
    };
    TestBed.configureTestingModule({
      imports: [ExportAssistant],
      providers: [
        { provide: ChironApi, useValue: chironApi },
        { provide: Router, useValue: router },
        { provide: AuthService, useValue: { getUsername: () => 'alice' } },
        { provide: TelechargementExport, useValue: telechargementExport },
        { provide: ActivatedRoute, useValue: {} },
      ],
    });
  });

  afterEach(() => vi.restoreAllMocks());

  describe('étape 1 : séances du programme', () => {
    it('charge les séances du programme avec leur nombre d exercices et de séries', async () => {
      await monter();

      expect(chironApi.getProgrammes).toHaveBeenCalledWith('alice');
      expect(composant.routines()[0]).toEqual({
        id: 5,
        titre: 'Push A',
        nbExercices: 2,
        nbSeries: 3,
      });
      expect(texte()).toContain('Étape 1 sur 3');
    });

    it('ne laisse pas avancer sans séance cochée', async () => {
      await monter();

      expect(composant.peutAvancer()).toBe(false);
      composant.suivant();
      expect(composant.etape()).toBe(1);
    });

    it('coche et décoche une séance', async () => {
      await monter();
      const routine = composant.routines()[0];

      composant.basculerRoutine(routine);
      expect(composant.estCochee(routine)).toBe(true);
      composant.basculerRoutine(routine);
      expect(composant.estCochee(routine)).toBe(false);
    });

    it('coche tout puis décoche tout', async () => {
      await monter();

      composant.basculerToutesLesRoutines();
      expect(composant.routinesChoisies()).toHaveLength(3);
      composant.basculerToutesLesRoutines();
      expect(composant.routinesChoisies()).toHaveLength(0);
    });

    it('affiche une erreur quand le chargement échoue', async () => {
      chironApi.getHistorique.mockReturnValue(throwError(() => new Error('500')));
      await monter();

      expect(composant.erreurChargement()).toBe(true);
      expect(texte()).toContain('Impossible de charger ton programme');
    });

    it('annule en revenant à la liste des exports', async () => {
      await monter();

      composant.precedent();

      expect(router.navigate).toHaveBeenCalledWith(['/exports']);
    });
  });

  describe('étape 2 : séances déjà faites', () => {
    beforeEach(async () => {
      await monter();
      cocherRoutines(5, 6);
      composant.suivant();
      fixture.detectChanges();
    });

    it('n affiche que les séances terminées, groupées par semaine', () => {
      expect(composant.etape()).toBe(2);
      expect(composant.journal().map((s) => s.id)).toEqual([30, 31, 32]);
      expect(composant.semaines().map((s) => s.cle)).toEqual(['2026-11-09', '2026-11-02']);
    });

    it('rattache automatiquement une séance dont le modèle fait partie de la sélection', () => {
      const automatique = faiteNumero(30);

      expect(composant.estAutomatique(automatique)).toBe(true);
      expect(composant.nbAutomatiques()).toBe(1);
      expect(texte()).toContain('Rattachée automatiquement à « Push A »');
    });

    it('ne rattache pas automatiquement une séance dont le modèle est hors sélection', () => {
      expect(composant.estAutomatique(faiteNumero(32))).toBe(false);
      expect(composant.estAutomatique(faiteNumero(31))).toBe(false);
    });

    it('exige un onglet pour chaque séance ajoutée à la main', () => {
      composant.basculerSeance(faiteNumero(31));
      fixture.detectChanges();

      expect(composant.estAjoutee(faiteNumero(31))).toBe(true);
      expect(composant.rattachementsIncomplets()).toBe(1);
      expect(composant.peutAvancer()).toBe(false);
      expect(texte()).toContain('Choisis un onglet pour 1 séance');
    });

    it('autorise la suite une fois l onglet choisi', () => {
      composant.basculerSeance(faiteNumero(31));

      composant.choisirCible(faiteNumero(31), '6');

      expect(composant.cible(faiteNumero(31))).toBe(6);
      expect(composant.rattachementsIncomplets()).toBe(0);
      expect(composant.peutAvancer()).toBe(true);
    });

    it('permet de revenir sur un onglet vide', () => {
      composant.basculerSeance(faiteNumero(31));
      composant.choisirCible(faiteNumero(31), '6');

      composant.choisirCible(faiteNumero(31), '');

      expect(composant.cible(faiteNumero(31))).toBeNull();
    });

    it('oublie l onglet quand la séance du programme est décochée à l étape 1', () => {
      composant.basculerSeance(faiteNumero(31));
      composant.choisirCible(faiteNumero(31), '6');
      composant.precedent();

      cocherRoutines(6);

      expect(composant.cible(faiteNumero(31))).toBeNull();
    });

    it('le select ne propose que les séances du programme choisies', () => {
      composant.basculerSeance(faiteNumero(31));
      fixture.detectChanges();

      const options = Array.from(
        (fixture.nativeElement as HTMLElement).querySelectorAll('select option'),
      ).map((option) => option.textContent?.trim());

      expect(options).toEqual(['Choisir un onglet', 'Push A', 'Pull A']);
    });

    it('verrouille la case d une séance rattachée automatiquement', () => {
      fixture.detectChanges();

      const cases = (fixture.nativeElement as HTMLElement).querySelectorAll<HTMLInputElement>(
        'input[type="checkbox"]',
      );

      const verrouillees = Array.from(cases).filter((c) => c.disabled);
      expect(verrouillees).toHaveLength(1);
      expect(verrouillees[0].checked).toBe(true);
    });
  });

  describe('étape 3 et enregistrement', () => {
    async function allerAuRecapitulatif() {
      await monter();
      cocherRoutines(5, 6);
      composant.suivant();
      composant.basculerSeance(faiteNumero(31));
      composant.choisirCible(faiteNumero(31), '6');
      composant.suivant();
    }

    it('propose un nom par défaut à partir de la date', async () => {
      await allerAuRecapitulatif();

      expect(composant.etape()).toBe(3);
      expect(composant.nom()).toMatch(/^Programme — /);
    });

    it('garde le nom déjà saisi quand on revient puis qu on avance à nouveau', async () => {
      await allerAuRecapitulatif();
      composant.saisirNom('Mon plan');
      composant.precedent();

      composant.suivant();

      expect(composant.nom()).toBe('Mon plan');
    });

    it('refuse un nom vide', async () => {
      await allerAuRecapitulatif();

      composant.saisirNom('   ');

      expect(composant.peutAvancer()).toBe(false);
    });

    it('envoie la sélection : modèles cochés, séances ajoutées à la main sans les automatiques', async () => {
      await allerAuRecapitulatif();
      composant.saisirNom('  Mon plan ');

      composant.suivant();

      expect(chironApi.creerExport).toHaveBeenCalledWith({
        nom: 'Mon plan',
        modeleIds: [5, 6],
        seances: [{ seanceId: 31, modeleId: 6 }],
      });
      expect(composant.resultat()?.id).toBe(12);
      expect(composant.enregistrement()).toBe(false);
    });

    it('n envoie pas de rattachement explicite pour une séance devenue automatique', async () => {
      await monter();
      cocherRoutines(5);
      composant.suivant();
      composant.basculerSeance(faiteNumero(30));
      composant.suivant();

      composant.suivant();

      expect(chironApi.creerExport).toHaveBeenCalledWith(expect.objectContaining({ seances: [] }));
    });

    it("affiche un message lisible quand l'enregistrement échoue", async () => {
      chironApi.creerExport.mockReturnValue(
        throwError(() => new HttpErrorResponse({ status: 500 })),
      );
      await allerAuRecapitulatif();

      composant.suivant();

      expect(composant.erreur()).toBe("L'export n'a pas pu être enregistré.");
      expect(composant.resultat()).toBeNull();
      expect(composant.enregistrement()).toBe(false);
    });

    it('n envoie pas deux fois la même requête pendant l enregistrement', async () => {
      chironApi.creerExport.mockReturnValue(new (await import('rxjs')).Subject());
      await allerAuRecapitulatif();

      composant.suivant();
      composant.suivant();

      expect(chironApi.creerExport).toHaveBeenCalledTimes(1);
    });

    it('affiche l écran de succès avec le lien du classeur', async () => {
      await allerAuRecapitulatif();
      composant.suivant();
      fixture.detectChanges();

      expect(texte()).toContain('Ton export est prêt');
      expect(texte()).toContain('Mon export');
    });

    it("télécharge le fichier depuis l'écran de succès", async () => {
      await allerAuRecapitulatif();
      composant.suivant();

      await composant.telecharger();

      expect(telechargementExport.telecharger).toHaveBeenCalledWith(
        expect.objectContaining({ id: 12 }),
      );
      expect(composant.telechargementEnCours()).toBe(false);
    });

    it('signale un téléchargement impossible', async () => {
      telechargementExport.telecharger.mockRejectedValue(new Error('503'));
      await allerAuRecapitulatif();
      composant.suivant();

      await composant.telecharger();

      expect(composant.erreur()).toBe('Impossible de télécharger le fichier.');
      expect(composant.telechargementEnCours()).toBe(false);
    });
  });

  describe('modification d un export existant', () => {
    it('recharge la sélection de l export', async () => {
      await monter('9');

      expect(chironApi.getExport).toHaveBeenCalledWith(9);
      expect(composant.enEdition()).toBe(true);
      expect(composant.nom()).toBe('Mon export');
      expect([...composant.modelesChoisis()]).toEqual([6]);
      expect(composant.cible(faiteNumero(31))).toBe(6);
      expect(composant.estAjoutee(faiteNumero(31))).toBe(true);
    });

    it('enregistre avec une modification et non une création', async () => {
      await monter('9');
      composant.suivant();
      composant.suivant();

      composant.suivant();

      expect(chironApi.modifierExport).toHaveBeenCalledWith(9, {
        nom: 'Mon export',
        modeleIds: [6],
        seances: [{ seanceId: 31, modeleId: 6 }],
      });
      expect(chironApi.creerExport).not.toHaveBeenCalled();
    });

    it('affiche une erreur quand l export n existe plus', async () => {
      chironApi.getExport.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 404 })));
      await monter('9');

      expect(composant.erreurChargement()).toBe(true);
    });
  });
});
