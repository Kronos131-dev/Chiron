import { CoursePointDto, CourseSplitDto, CourseTraceDto } from '../service/chiron-api';
import { wodSpec } from '../shared/wod-specs';
import { WodType } from '../shared/exercise-forms';
import { durationMinutes } from '../util/duration';
import { formaterAllure, formaterChrono } from '../util/allure';
import {
  ExerciceBrut,
  chiffreRomain,
  compterReps,
  compterSeries,
  estMuscu,
  formaterDureeMinutes,
  formaterNombre,
  formaterTonnage,
  meilleureSerie,
  tonnageExercice,
  tonnageTotal,
} from './bilan';

export type GenreCarte = 'bataille' | 'course' | 'wod';

export type Traducteur = (cle: string, params?: Record<string, string | number>) => string;

export interface Athlete {
  username: string;
  niveau: number | null;
  palier: string | null;
}

export interface Tuile {
  valeur: string;
  unite: string;
  cle: string;
}

export interface LigneExercice {
  numero: string;
  nom: string;
  detail: string;
  sousDetail: string | null;
  part: number;
}

export interface CarteBataille {
  tuiles: Tuile[];
  lignes: LigneExercice[];
  exercices: number;
}

export interface CarteCourse {
  distanceKm: string;
  dureeS: number;
  allureKmh: number;
  denivelePositifM: number | null;
  points: CoursePointDto[];
  splits: CourseSplitDto[];
  objectifAtteint: boolean;
  tuiles: Tuile[];
}

export interface MouvementWod {
  cle: string;
  total: number;
}

export interface CarteWod {
  tours: number;
  record: number | null;
  recordBattu: boolean;
  dureeMin: number;
  mouvements: MouvementWod[];
  repsTotal: number;
}

export interface ActiviteMontre {
  fcMoyenne?: number | null;
  calories?: number | null;
}

export interface CarteSeance {
  genre: GenreCarte;
  titre: string;
  date: Date;
  athlete: Athlete;
  bataille: CarteBataille | null;
  course: CarteCourse | null;
  wod: CarteWod | null;
  activite: ActiviteMontre | null;
}

export interface SeanceBrute {
  titre?: string | null;
  startTime?: string | null;
  endTime?: string | null;
  exercices?: ExerciceBrut[] | null;
}

const NOMS_COURTS_MOUVEMENTS: Record<string, string> = {
  'wod.cindy.pullups': 'share.wod.pullups',
  'wod.cindy.pushups': 'share.wod.pushups',
  'wod.cindy.squats': 'share.wod.squats',
};
const METRES_PAR_KM = 1000;

export function choisirGenre(exercices: ExerciceBrut[], tracePresente: boolean): GenreCarte {
  if (exercices.length !== 1) return 'bataille';
  const seul = exercices[0];
  if (seul.cardioType === 'COURSE_EXTERIEUR' && tracePresente) return 'course';
  if (seul.wodType) return 'wod';
  return 'bataille';
}

function activiteUtile(activite: ActiviteMontre | null | undefined): ActiviteMontre | null {
  if (!activite) return null;
  return activite.fcMoyenne || activite.calories ? activite : null;
}

function ligneMuscu(
  exercice: ExerciceBrut,
  numero: number,
  tonnageMax: number,
  t: Traducteur,
  langue: string,
): LigneExercice {
  const meilleure = meilleureSerie(exercice);
  const nbSeries = (exercice.series ?? []).length;
  const tonnage = tonnageExercice(exercice);
  const charge =
    meilleure && meilleure.poids > 0
      ? `${formaterNombre(meilleure.poids, langue, 1)} kg`
      : t('share.pdc');
  const mesure = formaterTonnage(tonnage, langue);
  const volume = tonnage > 0 ? ` · ${mesure.valeur} ${mesure.unite}` : '';
  return {
    numero: chiffreRomain(numero),
    nom: exercice.nom ?? '',
    detail: meilleure ? `${charge} × ${meilleure.reps}` : '—',
    sousDetail: `${t(nbSeries > 1 ? 'share.seriesCount' : 'share.seriesCount1', { n: nbSeries })}${volume}`,
    part: tonnageMax > 0 ? tonnage / tonnageMax : 0,
  };
}

function ligneCardio(
  exercice: ExerciceBrut,
  numero: number,
  t: Traducteur,
  langue: string,
): LigneExercice {
  const series = exercice.series ?? [];
  const distanceM = series.reduce((somme, s) => somme + (s.distanceM ?? 0), 0);
  const dureeMin = series.reduce((somme, s) => somme + (s.dureeMin ?? 0), 0);
  const calories = series.reduce((somme, s) => somme + (s.calories ?? 0), 0);
  const parties: string[] = [];
  if (distanceM > 0) parties.push(`${formaterNombre(distanceM / METRES_PAR_KM, langue, 2)} km`);
  if (dureeMin > 0) parties.push(`${Math.round(dureeMin)} min`);
  return {
    numero: chiffreRomain(numero),
    nom: exercice.nom ?? '',
    detail: parties.join(' · ') || '—',
    sousDetail: calories > 0 ? `${Math.round(calories)} kcal` : null,
    part: 0,
  };
}

function ligneWod(exercice: ExerciceBrut, numero: number, t: Traducteur): LigneExercice {
  const tours = exercice.series?.[0]?.reps ?? 0;
  return {
    numero: chiffreRomain(numero),
    nom: exercice.nom ?? '',
    detail: `${tours} ${t('wod.rounds')}`,
    sousDetail: null,
    part: 0,
  };
}

export function lignesDesExercices(
  exercices: ExerciceBrut[],
  t: Traducteur,
  langue: string,
): LigneExercice[] {
  const tonnageMax = Math.max(0, ...exercices.map(tonnageExercice));
  return exercices.map((exercice, index) => {
    if (exercice.wodType) return ligneWod(exercice, index + 1, t);
    if (exercice.cardioType) return ligneCardio(exercice, index + 1, t, langue);
    return ligneMuscu(exercice, index + 1, tonnageMax, t, langue);
  });
}

function bataille(
  exercices: ExerciceBrut[],
  dureeMin: number | null,
  t: Traducteur,
  langue: string,
): CarteBataille {
  const tonnage = formaterTonnage(tonnageTotal(exercices), langue);
  const aDuMuscu = exercices.some(estMuscu);
  const tuiles: Tuile[] = [
    { cle: 'share.stat.duree', valeur: formaterDureeMinutes(dureeMin), unite: '' },
    {
      cle: 'share.stat.tonnage',
      valeur: aDuMuscu ? tonnage.valeur : '—',
      unite: aDuMuscu ? tonnage.unite : '',
    },
    {
      cle: 'share.stat.series',
      valeur: formaterNombre(compterSeries(exercices), langue),
      unite: '',
    },
    { cle: 'share.stat.reps', valeur: formaterNombre(compterReps(exercices), langue), unite: '' },
  ];
  return { tuiles, lignes: lignesDesExercices(exercices, t, langue), exercices: exercices.length };
}

function wod(exercice: ExerciceBrut, record: number | null, recordBattu: boolean): CarteWod {
  const spec = wodSpec((exercice.wodType ?? null) as WodType | null);
  const tours = exercice.series?.[0]?.reps ?? 0;
  const mouvements = (spec?.mouvements ?? []).map((cle, index) => ({
    cle: NOMS_COURTS_MOUVEMENTS[cle] ?? cle,
    total: tours * (spec?.repsParTour[index] ?? 0),
  }));
  return {
    tours,
    record,
    recordBattu,
    dureeMin: spec?.dureeMin ?? exercice.series?.[0]?.dureeMin ?? 0,
    mouvements,
    repsTotal: mouvements.reduce((somme, mouvement) => somme + mouvement.total, 0),
  };
}

export function carteDeCourse(trace: CourseTraceDto, t: Traducteur, langue: string): CarteCourse {
  const dureeS = trace.dureeS;
  const tuiles: Tuile[] = [
    { cle: 'course.duration', valeur: formaterChrono(dureeS), unite: '' },
    {
      cle: 'course.pace',
      valeur: formaterAllure(trace.allureMoyenneKmh),
      unite: t('course.paceUnitShort.minParKm'),
    },
  ];
  const denivele = trace.denivelePositifM > 0 ? trace.denivelePositifM : null;
  if (denivele !== null) {
    tuiles.push({
      cle: 'share.stat.denivele',
      valeur: formaterNombre(Math.round(denivele), langue),
      unite: 'm',
    });
  }
  return {
    distanceKm: formaterNombre(trace.distanceM / METRES_PAR_KM, langue, 2),
    dureeS,
    allureKmh: trace.allureMoyenneKmh,
    denivelePositifM: denivele,
    points: trace.points,
    splits: trace.splits,
    objectifAtteint: (trace.objectifDureeS ?? 0) > 0,
    tuiles,
  };
}

function dateDeSeance(debut: string | null | undefined): Date {
  const date = debut ? new Date(debut) : new Date();
  return Number.isNaN(date.getTime()) ? new Date() : date;
}

export function titreParDefaut(titre: string | null | undefined, t: Traducteur): string {
  return titre?.trim() || t('journal.workout');
}

export interface EntreeSeance {
  seance: SeanceBrute;
  traces: ReadonlyMap<number, CourseTraceDto>;
  activite: ActiviteMontre | null;
  athlete: Athlete;
  t: Traducteur;
  langue: string;
}

export function carteDeSeance(entree: EntreeSeance): CarteSeance {
  const { seance, traces, athlete, t, langue } = entree;
  const exercices = seance.exercices ?? [];
  const idTrace = exercices.length === 1 ? exercices[0].series?.[0]?.courseTraceId : null;
  const trace = idTrace ? (traces.get(idTrace) ?? null) : null;
  const genre = choisirGenre(exercices, trace !== null);
  const dureeMin = durationMinutes(seance.startTime ?? null, seance.endTime ?? null);
  const base = {
    genre,
    titre: titreParDefaut(seance.titre ?? exercices[0]?.nom, t),
    date: dateDeSeance(seance.startTime),
    athlete,
    activite: activiteUtile(entree.activite),
  };
  if (genre === 'course' && trace) {
    const course = carteDeCourse(trace, t, langue);
    return { ...base, bataille: null, course, wod: null };
  }
  if (genre === 'wod') {
    return { ...base, bataille: null, course: null, wod: wod(exercices[0], null, false) };
  }
  return { ...base, bataille: bataille(exercices, dureeMin, t, langue), course: null, wod: null };
}

export function carteDeSortie(
  trace: CourseTraceDto,
  nom: string,
  date: Date,
  athlete: Athlete,
  t: Traducteur,
  langue: string,
): CarteSeance {
  return {
    genre: 'course',
    titre: titreParDefaut(nom, t),
    date,
    athlete,
    bataille: null,
    course: carteDeCourse(trace, t, langue),
    wod: null,
    activite: null,
  };
}

export function carteDeWod(
  exercice: ExerciceBrut,
  tours: number,
  record: number | null,
  recordBattu: boolean,
  date: Date,
  athlete: Athlete,
  t: Traducteur,
): CarteSeance {
  const avecTours: ExerciceBrut = {
    ...exercice,
    series: [{ ...(exercice.series?.[0] ?? {}), reps: tours }],
  };
  return {
    genre: 'wod',
    titre: titreParDefaut(exercice.nom, t),
    date,
    athlete,
    bataille: null,
    course: null,
    wod: wod(avecTours, record, recordBattu),
    activite: null,
  };
}

export function texteAccompagnement(carte: CarteSeance, t: Traducteur): string {
  if (carte.genre === 'course' && carte.course) {
    return t('share.text.course', {
      km: carte.course.distanceKm,
      temps: formaterChrono(carte.course.dureeS),
    });
  }
  if (carte.genre === 'wod' && carte.wod) {
    return t('share.text.wod', { tours: carte.wod.tours, min: carte.wod.dureeMin });
  }
  const tuiles = carte.bataille?.tuiles ?? [];
  const duree = tuiles.find((tuile) => tuile.cle === 'share.stat.duree')?.valeur ?? '—';
  const tonnage = tuiles.find((tuile) => tuile.cle === 'share.stat.tonnage');
  return t('share.text.bataille', {
    titre: carte.titre,
    duree,
    tonnage: tonnage ? `${tonnage.valeur} ${tonnage.unite}`.trim() : '—',
  });
}
