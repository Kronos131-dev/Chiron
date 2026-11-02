import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { of } from 'rxjs';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { Session } from './session';
import { ChironApi } from '../../service/chiron-api';
import { AuthService } from '../../service/auth.service';

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
                titre: 'Push',
                exercices: [
                  {
                    nom: 'Développé couché',
                    series: [{ poids: 100, reps: 8, degressifs: [] }],
                  },
                ],
              }),
            ),
            sauvegarderProgramme,
            searchExercices: vi.fn().mockReturnValue(of([])),
            getLastPerformance: vi.fn().mockReturnValue(of({ series: [] })),
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

  it('ne rattache aucun modèle à une séance interactive, qui en est une elle-même', async () => {
    const { session, sauvegarderProgramme } = await monter({ interactive: 'true' });

    session.ajouterAuJournal();

    const [, journal] = sauvegarderProgramme.mock.calls[0];
    expect(journal).toMatchObject({ id: 7, modeleId: null, historique: true });
  });
});
