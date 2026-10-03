import { describe, expect, it } from 'vitest';
import {
  chiffreRomain,
  compterReps,
  compterSeries,
  formaterDureeMinutes,
  formaterTonnage,
  meilleureSerie,
  tonnageExercice,
  tonnageTotal,
} from './bilan';

const developpe = {
  nom: 'Développé couché',
  series: [
    { poids: 100, reps: 8, degressifs: [{ poids: 80, reps: 6 }] },
    { poids: 100, reps: 10 },
    { poids: 90, reps: 12 },
  ],
};

describe('bilan', () => {
  it('somme poids x reps, dégressifs compris', () => {
    expect(tonnageExercice(developpe)).toBe(800 + 480 + 1000 + 1080);
  });

  it('double le tonnage d un exercice unilatéral', () => {
    expect(tonnageExercice({ ...developpe, unilateral: true })).toBe(2 * 3360);
  });

  it('ignore le cardio et le WOD dans le tonnage', () => {
    const exercices = [
      developpe,
      { nom: 'Rameur', cardioType: 'RAMEUR', series: [{ dureeMin: 10 }] },
    ];
    expect(tonnageTotal(exercices)).toBe(3360);
  });

  it('retient la charge la plus lourde puis le plus de reps', () => {
    expect(meilleureSerie(developpe)).toEqual({ poids: 100, reps: 10 });
  });

  it('accepte le poids du corps comme meilleure série', () => {
    const tractions = {
      nom: 'Tractions',
      series: [
        { poids: 0, reps: 8 },
        { poids: 0, reps: 12 },
      ],
    };
    expect(meilleureSerie(tractions)).toEqual({ poids: 0, reps: 12 });
  });

  it('ne retourne rien sans série valide', () => {
    expect(meilleureSerie({ nom: 'Vide', series: [{ poids: 50, reps: 0 }] })).toBeNull();
  });

  it('compte séries et reps de la musculation seulement', () => {
    const exercices = [developpe, { nom: 'Cindy', wodType: 'CINDY', series: [{ reps: 18 }] }];
    expect(compterSeries(exercices)).toBe(3);
    expect(compterReps(exercices)).toBe(8 + 6 + 10 + 12);
  });

  it('écrit les chiffres romains', () => {
    expect([1, 2, 4, 5, 9, 12].map(chiffreRomain)).toEqual(['I', 'II', 'IV', 'V', 'IX', 'XII']);
  });

  it('passe en tonnes au-delà d une tonne', () => {
    expect(formaterTonnage(762, 'fr')).toEqual({ valeur: '762', unite: 'kg' });
    expect(formaterTonnage(12400, 'en')).toEqual({ valeur: '12.4', unite: 't' });
  });

  it('formate une durée en heures et minutes', () => {
    expect(formaterDureeMinutes(45)).toBe('45 min');
    expect(formaterDureeMinutes(72)).toBe('1 h 12');
    expect(formaterDureeMinutes(null)).toBe('—');
  });
});
