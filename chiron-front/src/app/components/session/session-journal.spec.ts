import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { of } from 'rxjs';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { Session } from './session';
import { ChironApi, ExerciceDefinitionDto } from '../../service/chiron-api';
import { AuthService } from '../../service/auth.service';

function definition(id: number, nom: string): ExerciceDefinitionDto {
  return {
    id,
    nomFr: nom,
    nomEn: nom,
    cardioType: null,
    wodType: null,
  } as unknown as ExerciceDefinitionDto;
}

describe('Session — séance enregistrée au journal', () => {
  afterEach(() => localStorage.clear());

  async function monter(queryParams: Record<string, string>) {
    localStorage.clear();
    const sauvegarderProgramme = vi.fn().mockReturnValue(of(''));
    TestBed.resetTestingModule();
    await TestBed.configureTestingModule({
      imports: [Session],
      providers: [
        {
          provide: ChironApi,
          useValue: {
            getProgrammeById: vi.fn().mockReturnValue(
              of({
                id: 7,
                titre: 'Push',
                weekNumber: 0,
                exercices: [
                  {
                    id: 31,
                    nom: 'Développé couché',
                    series: [{ poids: 100, reps: 8, degressifs: [] }],
                  },
                  {
                    id: 32,
                    nom: 'Dips',
                    series: [{ poids: 10, reps: 10, degressifs: [] }],
                  },
                ],
              }),
            ),
            sauvegarderProgramme,
            searchExercices: vi.fn().mockReturnValue(of([])),
            getLastPerformance: vi
              .fn()
              .mockReturnValue(of({ series: [{ poids: 40, reps: 12, degressifs: [] }] })),
            getExerciceById: vi.fn().mockReturnValue(of({})),
          },
        },
        { provide: AuthService, useValue: { getUsername: vi.fn().mockReturnValue('alice') } },
        { provide: Router, useValue: { navigate: vi.fn() } },
        {
          provide: ActivatedRoute,
          useValue: {
            paramMap: of(convertToParamMap({ id: '7' })),
            queryParams: of(queryParams),
          },
        },
      ],
    }).compileComponents();

    const fixture = TestBed.createComponent(Session);
    fixture.componentInstance.ngOnInit();
    return { session: fixture.componentInstance, sauvegarderProgramme };
  }

  it('rattache la séance faite au modèle dont elle est partie', async () => {
    const { session, sauvegarderProgramme } = await monter({});

    session.ajouterAuJournal();

    const [, journal] = sauvegarderProgramme.mock.calls[0];
    expect(journal).toMatchObject({ id: null, modeleId: 7, historique: true });
  });

  it("n'écrit que dans le journal par défaut, sans toucher à la séance type", async () => {
    const { session, sauvegarderProgramme } = await monter({});

    session.ajouterAuJournal();

    expect(sauvegarderProgramme).toHaveBeenCalledTimes(1);
  });

  it('reporte les valeurs faites sur la séance type quand on le demande', async () => {
    const { session, sauvegarderProgramme } = await monter({});
    session.exercices()[0].series[0].poids = 105;

    session.ajouterAuJournal(true);

    expect(sauvegarderProgramme).toHaveBeenCalledTimes(2);
    const [, seanceType] = sauvegarderProgramme.mock.calls[1];
    expect(seanceType).toMatchObject({ id: 7, historique: false });
    expect(seanceType.exercices[0].series[0].poids).toBe(105);
  });

  it('remplace un exercice à sa place, sans le renvoyer en bas de la séance', async () => {
    const { session } = await monter({});
    const [developpe, dips] = session.exercices();

    session.ouvrirRemplacement(developpe);
    session.addExerciceFromDefinition(definition(50, 'Pompes'));

    expect(session.exercices().map((e) => e.nom)).toEqual(['Pompes', 'Dips']);
    expect(session.exercices()[1]).toBe(dips);
    expect(session.exercices()[0].series[0]).toMatchObject({ poids: 40, reps: 12 });
    expect(session.pickerOpen()).toBe(false);
    expect(session.exerciceARemplacer()).toBeNull();
  });

  it("ajoute toujours en fin de séance quand on n'a rien choisi de remplacer", async () => {
    const { session } = await monter({});

    session.openPicker();
    session.addExerciceFromDefinition(definition(50, 'Pompes'));

    expect(session.exercices().map((e) => e.nom)).toEqual(['Développé couché', 'Dips', 'Pompes']);
    expect(session.pickerOpen()).toBe(true);
  });

  it('ne rattache aucun modèle à une séance interactive, qui en est une elle-même', async () => {
    const { session, sauvegarderProgramme } = await monter({ interactive: 'true' });

    session.ajouterAuJournal();

    const [, journal] = sauvegarderProgramme.mock.calls[0];
    expect(journal).toMatchObject({ id: 7, modeleId: null, historique: true });
  });
});
