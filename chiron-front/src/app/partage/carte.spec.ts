import { describe, expect, it } from 'vitest';
import { CourseTraceDto } from '../service/chiron-api';
import { fr } from '../i18n/fr';
import { ExerciceBrut } from './bilan';
import {
  Athlete,
  Traducteur,
  carteDeSeance,
  carteDeWod,
  choisirGenre,
  texteAccompagnement,
} from './carte';
import { traceExemple } from './exemples';

const traducteurFr: Traducteur = (cle, params) =>
  (fr[cle] ?? cle).replace(/\{\{\s*(\w+)\s*\}\}/g, (_, nom: string) => String(params?.[nom] ?? ''));

const athlete: Athlete = { username: 'Kronos', niveau: 5, palier: 'Spartiate' };

const course: ExerciceBrut = {
  nom: 'Course extérieure',
  cardioType: 'COURSE_EXTERIEUR',
  series: [{ distanceM: 10500, dureeMin: 53, courseTraceId: 7 }],
};
const cindy: ExerciceBrut = {
  nom: 'Cindy',
  wodType: 'CINDY',
  series: [{ reps: 18, dureeMin: 20 }],
};
const squat: ExerciceBrut = {
  nom: 'Squat',
  series: [
    { poids: 120, reps: 5 },
    { poids: 120, reps: 5 },
  ],
};

function entree(exercices: ExerciceBrut[], traces = new Map<number, CourseTraceDto>()) {
  return {
    seance: {
      titre: 'Séance du jour',
      startTime: '2026-10-03T18:00:00',
      endTime: '2026-10-03T19:10:00',
      exercices,
    },
    traces,
    activite: null,
    athlete,
    t: traducteurFr,
    langue: 'fr',
  };
}

describe('carte', () => {
  it('choisit le genre selon le contenu de la séance', () => {
    expect(choisirGenre([course], true)).toBe('course');
    expect(choisirGenre([course], false)).toBe('bataille');
    expect(choisirGenre([cindy], false)).toBe('wod');
    expect(choisirGenre([squat, course], true)).toBe('bataille');
    expect(choisirGenre([], false)).toBe('bataille');
  });

  it('construit une bataille avec ses quatre tuiles et ses lignes numérotées', () => {
    const carte = carteDeSeance(entree([squat, course]));
    expect(carte.genre).toBe('bataille');
    expect(carte.bataille?.tuiles.map((tuile) => tuile.cle)).toEqual([
      'share.stat.duree',
      'share.stat.tonnage',
      'share.stat.series',
      'share.stat.reps',
    ]);
    expect(carte.bataille?.tuiles[0].valeur).toBe('1 h 10');
    expect(carte.bataille?.lignes.map((ligne) => ligne.numero)).toEqual(['I', 'II']);
    expect(carte.bataille?.lignes[0].detail).toBe('120 kg × 5');
  });

  it('construit une carte de course quand la trace est connue', () => {
    const trace = traceExemple();
    const carte = carteDeSeance(entree([course], new Map([[7, trace]])));
    expect(carte.genre).toBe('course');
    expect(carte.course?.objectifAtteint).toBe(true);
    expect(carte.course?.tuiles[0].cle).toBe('course.duration');
    expect(carte.bataille).toBeNull();
  });

  it('retombe sur une bataille quand la trace manque', () => {
    expect(carteDeSeance(entree([course])).genre).toBe('bataille');
  });

  it('construit une carte WOD avec le total de répétitions', () => {
    const carte = carteDeSeance(entree([cindy]));
    expect(carte.genre).toBe('wod');
    expect(carte.wod?.tours).toBe(18);
    expect(carte.wod?.repsTotal).toBe(540);
    expect(carte.wod?.mouvements).toEqual([
      { cle: 'share.wod.pullups', total: 90 },
      { cle: 'share.wod.pushups', total: 180 },
      { cle: 'share.wod.squats', total: 270 },
    ]);
  });

  it('marque le record battu sur la carte WOD', () => {
    const carte = carteDeWod(cindy, 19, 18, true, new Date(), athlete, traducteurFr);
    expect(carte.wod).toMatchObject({ tours: 19, record: 18, recordBattu: true });
  });

  it('ne garde l activité de la montre que si elle porte une valeur', () => {
    const avec = carteDeSeance({
      ...entree([squat]),
      activite: { fcMoyenne: 130, calories: null },
    });
    const sans = carteDeSeance({
      ...entree([squat]),
      activite: { fcMoyenne: null, calories: null },
    });
    expect(avec.activite).not.toBeNull();
    expect(sans.activite).toBeNull();
  });

  it('écrit un texte d accompagnement par genre', () => {
    const bataille = carteDeSeance(entree([squat]));
    expect(texteAccompagnement(bataille, traducteurFr)).toContain('chiron-sanctuaire.fr');
    expect(texteAccompagnement(bataille, traducteurFr)).toContain('Séance du jour');
    const wod = carteDeSeance(entree([cindy]));
    expect(texteAccompagnement(wod, traducteurFr)).toContain('18 tours de Cindy');
  });
});
