import { CoursePointDto, CourseTraceDto } from '../service/chiron-api';
import { mesurer } from '../service/course-tracker';
import { ExerciceBrut } from './bilan';
import {
  Athlete,
  CarteSeance,
  Traducteur,
  carteDeSeance,
  carteDeSortie,
  carteDeWod,
} from './carte';

const POINTS_EXEMPLE = 640;
const LATITUDE_CENTRE = 48.8566;
const LONGITUDE_CENTRE = 2.3522;
const PAS_ECHANTILLON_MS = 5000;

export function athleteExemple(niveau: number | null): Athlete {
  return {
    username: 'Kronos',
    niveau,
    palier: niveau ? (NOMS_PALIERS[niveau - 1] ?? null) : null,
  };
}

const NOMS_PALIERS = [
  'Éphèbe',
  'Argonaute',
  'Hoplite',
  'Myrmidon',
  'Spartiate',
  'Héros',
  'Dieu',
  'Olympien',
];

export function pointsExemple(): CoursePointDto[] {
  const depart = Date.UTC(2026, 9, 3, 7, 0, 0);
  return Array.from({ length: POINTS_EXEMPLE }, (_, index) => {
    const avancement = index / (POINTS_EXEMPLE - 1);
    const theta = 2 * Math.PI * (avancement + 0.04 * Math.sin(6 * Math.PI * avancement));
    return {
      lat: LATITUDE_CENTRE + 0.014 * Math.sin(theta) * (1 + 0.25 * Math.cos(3 * theta)),
      lon: LONGITUDE_CENTRE + 0.022 * Math.cos(theta) * (1 + 0.1 * Math.sin(2 * theta)),
      t: depart + index * PAS_ECHANTILLON_MS,
      alt: 35 + 12 * Math.sin(theta * 2),
    };
  });
}

export function traceExemple(): CourseTraceDto {
  const points = pointsExemple();
  const mesures = mesurer(points);
  const dureeS = (points[points.length - 1].t - points[0].t) / 1000;
  return {
    id: 0,
    distanceM: mesures.distanceM,
    dureeS,
    allureMoyenneKmh: mesures.distanceM / 1000 / (dureeS / 3600),
    denivelePositifM: 84,
    objectifDistanceM: 5000,
    objectifDureeS: 1620,
    splits: mesures.splits,
    points,
  };
}

const serie = (poids: number, reps: number) => ({ poids, reps, degressifs: [] });

export const EXERCICES_EXEMPLE: ExerciceBrut[] = [
  { nom: 'Développé couché', series: [serie(100, 8), serie(100, 8), serie(95, 10), serie(90, 12)] },
  {
    nom: 'Développé incliné haltères',
    unilateral: true,
    series: [serie(32, 10), serie(32, 9), serie(30, 10)],
  },
  { nom: 'Écartés à la poulie', series: [serie(18, 15), serie(18, 14), serie(16, 15)] },
  { nom: 'Tractions lestées', series: [serie(10, 8), serie(10, 7), serie(0, 12)] },
  { nom: 'Rowing barre', series: [serie(80, 10), serie(80, 10), serie(75, 12)] },
  { nom: 'Curl marteau', series: [serie(16, 12), serie(16, 11)] },
  {
    nom: 'Rameur',
    cardioType: 'RAMEUR',
    series: [{ dureeMin: 10, distanceM: 2400, calories: 120 }],
  },
  { nom: 'Cindy', wodType: 'CINDY', series: [{ reps: 18, poids: 0, dureeMin: 20 }] },
];

export function carteBatailleExemple(
  niveau: number | null,
  t: Traducteur,
  langue: string,
): CarteSeance {
  return carteDeSeance({
    seance: {
      titre: 'Push — pecs & épaules',
      startTime: '2026-10-03T18:05:00',
      endTime: '2026-10-03T19:17:00',
      exercices: EXERCICES_EXEMPLE,
    },
    traces: new Map(),
    activite: { fcMoyenne: 132, calories: 540 },
    athlete: athleteExemple(niveau),
    t,
    langue,
  });
}

export function carteCourseExemple(
  niveau: number | null,
  t: Traducteur,
  langue: string,
): CarteSeance {
  return carteDeSortie(
    traceExemple(),
    'Course extérieure',
    new Date(2026, 9, 3, 7, 0),
    athleteExemple(niveau),
    t,
    langue,
  );
}

export function carteWodExemple(niveau: number | null, t: Traducteur): CarteSeance {
  return carteDeWod(
    { nom: 'Cindy', wodType: 'CINDY', series: [{ reps: 18, poids: 0, dureeMin: 20 }] },
    18,
    17,
    true,
    new Date(2026, 9, 3, 18, 0),
    athleteExemple(niveau),
    t,
  );
}
