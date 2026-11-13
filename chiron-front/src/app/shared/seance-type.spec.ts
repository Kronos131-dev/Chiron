import { describe, expect, it } from 'vitest';

import { seanceTypeAvecExercicesDe, seanceTypeAvecValeursDe } from './seance-type';
import { ExerciceForm } from './exercise-forms';

function seanceType(): any {
  return {
    id: 7,
    titre: 'Push',
    note: 'Échauffement long',
    weekNumber: 0,
    exercices: [
      {
        id: 31,
        nom: 'Développé couché',
        exerciceDefinitionId: 4,
        commentaire: 'Ancienne note',
        blockId: 2,
        blockType: 'SUPERSET',
        series: [{ poids: 100, reps: 8, degressifs: [] }],
      },
      {
        id: 32,
        nom: 'Dips',
        commentaire: 'Lest à prévoir',
        series: [{ poids: 10, reps: 10, degressifs: [] }],
      },
    ],
  };
}

function fait(id: number | string, nom: string, poids: number, extra: Partial<ExerciceForm> = {}) {
  return {
    id,
    nom,
    commentaire: '',
    series: [{ id: 'serie', poids, reps: 6, degressifs: [], courseTraceId: 99 }],
    ...extra,
  } as ExerciceForm;
}

describe('seanceTypeAvecValeursDe', () => {
  it('reports the values done onto the matching exercises of the template', () => {
    const resultat = seanceTypeAvecValeursDe(seanceType(), [
      fait(31, 'Développé couché', 105, { commentaire: 'Nouvelle note', unilateral: true }),
    ]);

    expect(resultat.exercices[0]).toMatchObject({
      nom: 'Développé couché',
      commentaire: 'Nouvelle note',
      unilateral: true,
      blockId: 2,
      blockType: 'SUPERSET',
    });
    expect(resultat.exercices[0].series[0]).toMatchObject({ poids: 105, reps: 6 });
  });

  it('keeps the list of exercises of the template, whatever was added, removed or replaced', () => {
    const resultat = seanceTypeAvecValeursDe(seanceType(), [
      fait('remplacant', 'Pompes', 0),
      fait('ajoute', 'Écarté', 20),
    ]);

    expect(resultat.exercices.map((e: any) => e.nom)).toEqual(['Développé couché', 'Dips']);
    expect(resultat.exercices[0].series).toEqual([{ poids: 100, reps: 8, degressifs: [] }]);
    expect(resultat.exercices[1].commentaire).toBe('Lest à prévoir');
  });

  it('keeps the title and the note of the template and never carries a GPS trace into it', () => {
    const resultat = seanceTypeAvecValeursDe(seanceType(), [fait(31, 'Développé couché', 105)]);

    expect(resultat).toMatchObject({
      id: 7,
      titre: 'Push',
      note: 'Échauffement long',
      historique: false,
    });
    expect(resultat.exercices[0].series[0].courseTraceId).toBeNull();
  });
});

describe('seanceTypeAvecExercicesDe', () => {
  it('takes the list of exercises done, removals and replacements included, in their order', () => {
    const resultat = seanceTypeAvecExercicesDe(seanceType(), [
      fait('remplacant', 'Pompes', 0),
      fait(31, 'Développé couché', 105, { commentaire: 'Nouvelle note' }),
    ]);

    expect(resultat.exercices.map((e: any) => e.nom)).toEqual(['Pompes', 'Développé couché']);
    expect(resultat.exercices[1]).toMatchObject({ commentaire: 'Nouvelle note', blockId: 2 });
    expect(resultat.exercices[1].series[0]).toMatchObject({ poids: 105, courseTraceId: null });
    expect(resultat.exercices[0].blockId).toBeNull();
  });

  it('keeps the title and the note of the template', () => {
    const resultat = seanceTypeAvecExercicesDe(seanceType(), [fait(31, 'Développé couché', 105)]);

    expect(resultat).toMatchObject({
      id: 7,
      titre: 'Push',
      note: 'Échauffement long',
      historique: false,
    });
  });
});
