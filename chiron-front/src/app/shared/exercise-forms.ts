/**
 * Form models shared by Session (workout execution), ProgrammeBuilder (programme edit/create),
 * and ExerciceCard (the inline editable exercise sub-component).
 *
 * Kept here so all three stay in sync — adding a field to ExerciceForm must apply everywhere.
 */

import { WOD_SPECS } from './wod-specs';

export interface DegressifForm {
  id: number | string;
  poids: number | null;
  reps: number | null;
}

export interface SerieForm {
  id: number | string;
  poids: number | null;
  reps: number | null;
  degressifs: DegressifForm[];
  // ── Paramètres cardio (null pour la musculation) ──
  dureeMin?: number | null;
  distanceM?: number | null;
  allureKmh?: number | null;
  pentePct?: number | null;
  /** Calories brûlées, calculées côté serveur (lecture seule côté front). */
  calories?: number | null;
  /** Trace GPS d'une sortie en extérieur, téléversée avant l'enregistrement de la séance. */
  courseTraceId?: number | null;
}

export type BlockType = 'SUPERSET' | 'BISET';

/** Types de cardio — miroir de l'enum backend {@code CardioType}. */
export type CardioType = 'MARCHE_PENTE' | 'COURSE' | 'COURSE_EXTERIEUR' | 'RAMEUR' | 'SKIERG';

export type WodType = 'CINDY';

export interface ExerciceForm {
  id: number | string;
  nom: string;
  definitionId?: number;
  /** Type de cardio si l'exercice en est un, sinon absent/null. */
  cardioType?: CardioType | null;
  wodType?: WodType | null;
  /** Note libre facultative sur l'exercice (ressenti, point à savoir, condition…). */
  commentaire?: string | null;
  /** Exercice réalisé en unilatéral (un membre à la fois) → tonnage ×2 + info pour Chiron. */
  unilateral?: boolean;
  series: SerieForm[];
  /**
   * Identifiant de groupe (superset/biset). Deux exercices consécutifs partageant
   * le même {@code blockId} sont enchaînés sans repos. Absent ou null = exo isolé.
   */
  blockId?: number | null;
  /** Nature du groupage. Cohérent entre tous les membres d'un même bloc. */
  blockType?: BlockType | null;
}

/**
 * Generate a UI-only identifier for dynamically created rows (exercice / serie / degressif).
 * The backend assigns the real numeric id on save; we only need uniqueness for `track` keys.
 */
export function generateFormId(): string {
  return Math.random().toString(36).substring(2, 11) + '_' + Date.now();
}

/**
 * Build a default empty exercise with one empty serie — used both by the "+ Add exercise"
 * action and as the seed when the user creates a brand-new programme.
 */
export function makeEmptyExercice(
  nom: string = '',
  definitionId?: number,
  cardioType?: CardioType | null,
  wodType?: WodType | null,
): ExerciceForm {
  return {
    id: generateFormId(),
    nom,
    definitionId,
    cardioType: cardioType ?? null,
    wodType: wodType ?? null,
    commentaire: '',
    unilateral: false,
    series: [makeSerieFor(cardioType, wodType)],
  };
}

export function makeSerieFor(cardioType?: CardioType | null, wodType?: WodType | null): SerieForm {
  if (wodType) return makeEmptyWodSerie(wodType);
  return cardioType ? makeEmptyCardioSerie() : makeEmptySerie();
}

export function makeEmptySerie(): SerieForm {
  return {
    id: generateFormId(),
    poids: null,
    reps: null,
    degressifs: [],
  };
}

/** Série cardio vide : pas de poids/reps, mais des paramètres d'effort. */
export function makeEmptyCardioSerie(): SerieForm {
  return {
    id: generateFormId(),
    poids: null,
    reps: null,
    degressifs: [],
    dureeMin: null,
    distanceM: null,
    allureKmh: null,
    pentePct: null,
    calories: null,
  };
}

export function makeEmptyWodSerie(wodType: WodType): SerieForm {
  return {
    id: generateFormId(),
    poids: 0,
    reps: null,
    degressifs: [],
    dureeMin: WOD_SPECS[wodType].dureeMin,
  };
}

export function makeEmptyDegressif(): DegressifForm {
  return {
    id: generateFormId(),
    poids: null,
    reps: null,
  };
}
