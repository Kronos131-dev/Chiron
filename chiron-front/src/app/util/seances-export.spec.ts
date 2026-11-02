import { describe, expect, it } from 'vitest';
import { grouperParSemaine, lundiDe, seancesTerminees } from './seances-export';

describe('seancesTerminees', () => {
  it('garde les séances terminées qui ont des exercices', () => {
    const resultat = seancesTerminees([
      {
        id: 1,
        titre: ' Push A ',
        startTime: '2026-11-02T10:00:00',
        endTime: '2026-11-02T11:00:00',
        modeleId: 5,
        exercices: [{}, {}],
      },
    ]);

    expect(resultat).toHaveLength(1);
    expect(resultat[0]).toMatchObject({ id: 1, titre: 'Push A', modeleId: 5, nbExercices: 2 });
  });

  it('écarte les séances sans fin, sans exercice ou sans date lisible', () => {
    const resultat = seancesTerminees([
      { id: 1, startTime: '2026-11-02T10:00:00', endTime: null, exercices: [{}] },
      { id: 2, startTime: '2026-11-02T10:00:00', endTime: '2026-11-02T11:00:00', exercices: [] },
      { id: 3, startTime: 'pas une date', endTime: '2026-11-02T11:00:00', exercices: [{}] },
      { id: 4, startTime: null, endTime: '2026-11-02T11:00:00', exercices: [{}] },
    ]);

    expect(resultat).toEqual([]);
  });

  it('laisse le modèle à null quand la séance n en a pas', () => {
    const [seance] = seancesTerminees([
      { id: 1, startTime: '2026-11-02T10:00:00', endTime: '2026-11-02T11:00:00', exercices: [{}] },
    ]);

    expect(seance.modeleId).toBeNull();
  });
});

describe('lundiDe', () => {
  it('rend le lundi de la même semaine pour un dimanche', () => {
    const lundi = lundiDe(new Date(2026, 10, 8, 18, 30));

    expect([lundi.getFullYear(), lundi.getMonth(), lundi.getDate()]).toEqual([2026, 10, 2]);
    expect(lundi.getHours()).toBe(0);
  });

  it('rend le jour même pour un lundi', () => {
    const lundi = lundiDe(new Date(2026, 10, 2, 9, 0));

    expect(lundi.getDate()).toBe(2);
  });
});

describe('grouperParSemaine', () => {
  const faite = (id: number, jour: number, mois = 10) => ({
    id,
    titre: `S${id}`,
    debut: new Date(2026, mois, jour, 10),
    modeleId: null,
    nbExercices: 1,
  });

  it('range les séances par semaine, la plus récente d abord', () => {
    const semaines = grouperParSemaine([faite(1, 3), faite(2, 10), faite(3, 5)]);

    expect(semaines.map((s) => s.cle)).toEqual(['2026-11-09', '2026-11-02']);
    expect(semaines[1].seances.map((s) => s.id)).toEqual([3, 1]);
  });

  it('distingue deux années qui partagent le même numéro de semaine', () => {
    const semaines = grouperParSemaine([
      faite(1, 3),
      { ...faite(2, 3), debut: new Date(2025, 10, 3) },
    ]);

    expect(semaines).toHaveLength(2);
  });
});
