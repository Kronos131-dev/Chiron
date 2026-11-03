import { describe, expect, it } from 'vitest';
import {
  SeanceFaite,
  grouperParSemaine,
  lundiDe,
  modeleAutomatique,
  modelesParTitre,
  normaliserTitre,
  seancesTerminees,
} from './seances-export';

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

describe('normaliserTitre', () => {
  it('ignore la casse, les bords et les blancs multiples', () => {
    expect(normaliserTitre('  PUSH   Day ')).toBe('push day');
  });

  it('rend une chaîne vide pour un titre absent', () => {
    expect(normaliserTitre(null)).toBe('');
    expect(normaliserTitre(undefined)).toBe('');
  });
});

describe('modelesParTitre', () => {
  it('associe chaque titre normalisé à sa séance du programme', () => {
    const parTitre = modelesParTitre([
      { id: 5, titre: 'Push' },
      { id: 6, titre: ' PULL ' },
    ]);

    expect(parTitre.get('push')).toBe(5);
    expect(parTitre.get('pull')).toBe(6);
  });

  it('écarte un titre porté par deux séances pour ne pas deviner', () => {
    const parTitre = modelesParTitre([
      { id: 5, titre: 'Push' },
      { id: 7, titre: 'push ' },
      { id: 6, titre: 'Pull' },
    ]);

    expect(parTitre.has('push')).toBe(false);
    expect(parTitre.get('pull')).toBe(6);
  });

  it('ignore les séances sans titre', () => {
    expect(modelesParTitre([{ id: 5, titre: '  ' }]).size).toBe(0);
  });
});

describe('modeleAutomatique', () => {
  const faite = (modeleId: number | null, titre: string): SeanceFaite => ({
    id: 1,
    titre,
    debut: new Date(2026, 10, 2),
    modeleId,
    nbExercices: 1,
  });
  const parTitre = new Map([['push', 5]]);

  it('suit le lien enregistré quand le modèle est dans la sélection', () => {
    expect(modeleAutomatique(faite(6, 'Push'), new Set([6]), parTitre)).toBe(6);
  });

  it('ne retombe pas sur le titre quand la séance est liée à un modèle hors sélection', () => {
    expect(modeleAutomatique(faite(9, 'Push'), new Set([5]), parTitre)).toBeNull();
  });

  it('rattache par titre une séance sans lien', () => {
    expect(modeleAutomatique(faite(null, ' push'), new Set([5]), parTitre)).toBe(5);
  });

  it('laisse sans rattachement une séance sans lien dont le titre est inconnu', () => {
    expect(modeleAutomatique(faite(null, 'Séance interactive'), new Set([5]), parTitre)).toBeNull();
  });
});
