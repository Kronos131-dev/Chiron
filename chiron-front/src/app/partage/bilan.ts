export interface DegressifBrut {
  poids?: number | null;
  reps?: number | null;
}

export interface SerieBrute {
  poids?: number | null;
  reps?: number | null;
  degressifs?: DegressifBrut[] | null;
  dureeMin?: number | null;
  distanceM?: number | null;
  allureKmh?: number | null;
  pentePct?: number | null;
  calories?: number | null;
  courseTraceId?: number | null;
}

export interface ExerciceBrut {
  nom?: string | null;
  cardioType?: string | null;
  wodType?: string | null;
  unilateral?: boolean | null;
  series?: SerieBrute[] | null;
}

export interface MeilleureSerie {
  poids: number;
  reps: number;
}

const ROMAINS: ReadonlyArray<readonly [number, string]> = [
  [10, 'X'],
  [9, 'IX'],
  [5, 'V'],
  [4, 'IV'],
  [1, 'I'],
];

export function estMuscu(exercice: ExerciceBrut): boolean {
  return !exercice.cardioType && !exercice.wodType;
}

function nombre(valeur: number | null | undefined): number {
  return Number.isFinite(valeur) ? Number(valeur) : 0;
}

export function tonnageExercice(exercice: ExerciceBrut): number {
  if (!estMuscu(exercice)) return 0;
  let total = 0;
  for (const serie of exercice.series ?? []) {
    total += nombre(serie.poids) * nombre(serie.reps);
    for (const degressif of serie.degressifs ?? []) {
      total += nombre(degressif.poids) * nombre(degressif.reps);
    }
  }
  return exercice.unilateral ? total * 2 : total;
}

export function tonnageTotal(exercices: ExerciceBrut[]): number {
  return exercices.reduce((somme, exercice) => somme + tonnageExercice(exercice), 0);
}

export function meilleureSerie(exercice: ExerciceBrut): MeilleureSerie | null {
  let meilleure: MeilleureSerie | null = null;
  for (const serie of exercice.series ?? []) {
    const reps = nombre(serie.reps);
    if (reps <= 0) continue;
    const candidate = { poids: nombre(serie.poids), reps };
    const plusLourde = !meilleure || candidate.poids > meilleure.poids;
    const memeChargePlusDeReps =
      meilleure && candidate.poids === meilleure.poids && candidate.reps > meilleure.reps;
    if (plusLourde || memeChargePlusDeReps) meilleure = candidate;
  }
  return meilleure;
}

export function compterSeries(exercices: ExerciceBrut[]): number {
  return exercices
    .filter(estMuscu)
    .reduce((somme, exercice) => somme + (exercice.series ?? []).length, 0);
}

export function compterReps(exercices: ExerciceBrut[]): number {
  return exercices.filter(estMuscu).reduce((somme, exercice) => {
    const parSerie = (exercice.series ?? []).reduce(
      (reps, serie) =>
        reps +
        nombre(serie.reps) +
        (serie.degressifs ?? []).reduce((d, degressif) => d + nombre(degressif.reps), 0),
      0,
    );
    return somme + parSerie;
  }, 0);
}

export function chiffreRomain(valeur: number): string {
  let reste = Math.max(0, Math.floor(valeur));
  let romain = '';
  for (const [pas, symbole] of ROMAINS) {
    while (reste >= pas) {
      romain += symbole;
      reste -= pas;
    }
  }
  return romain;
}

export function formaterNombre(valeur: number, langue: string, decimales = 0): string {
  return valeur.toLocaleString(langue, {
    minimumFractionDigits: 0,
    maximumFractionDigits: decimales,
  });
}

export interface Mesure {
  valeur: string;
  unite: string;
}

const KG_PAR_TONNE = 1000;

export function formaterTonnage(kg: number, langue: string): Mesure {
  if (kg >= KG_PAR_TONNE) {
    return { valeur: formaterNombre(kg / KG_PAR_TONNE, langue, 1), unite: 't' };
  }
  return { valeur: formaterNombre(Math.round(kg), langue), unite: 'kg' };
}

const MINUTES_PAR_HEURE = 60;

export function formaterDureeMinutes(minutes: number | null): string {
  if (minutes === null || minutes <= 0) return '—';
  if (minutes < MINUTES_PAR_HEURE) return `${Math.round(minutes)} min`;
  const heures = Math.floor(minutes / MINUTES_PAR_HEURE);
  const reste = Math.round(minutes % MINUTES_PAR_HEURE);
  return `${heures} h ${reste.toString().padStart(2, '0')}`;
}
