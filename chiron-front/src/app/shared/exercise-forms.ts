/**
 * Form models shared by Session (workout execution), ProgrammeBuilder (programme edit/create),
 * and ExerciceCard (the inline editable exercise sub-component).
 *
 * Kept here so all three stay in sync — adding a field to ExerciceForm must apply everywhere.
 */

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
}

export type BlockType = 'SUPERSET' | 'BISET';

/** Types de cardio — miroir de l'enum backend {@code CardioType}. */
export type CardioType = 'MARCHE_PENTE' | 'COURSE' | 'RAMEUR' | 'SKIERG';

export interface ExerciceForm {
  id: number | string;
  nom: string;
  definitionId?: number;
  /** Type de cardio si l'exercice en est un, sinon absent/null. */
  cardioType?: CardioType | null;
  series: SerieForm[];
  /**
   * Identifiant de groupe (superset/biset). Deux exercices consécutifs partageant
   * le même {@code blockId} sont enchaînés sans repos. Absent ou null = exo isolé.
   */
  blockId?: number | null;
  /** Nature du groupage. Cohérent entre tous les membres d'un même bloc. */
  blockType?: BlockType | null;
  /** Exercice réalisé en unilatéral (un membre à la fois) — double le tonnage. */
  unilateral?: boolean;
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
export function makeEmptyExercice(nom: string = '', definitionId?: number, cardioType?: CardioType | null): ExerciceForm {
  return {
    id: generateFormId(),
    nom,
    definitionId,
    cardioType: cardioType ?? null,
    unilateral: false,
    series: [cardioType ? makeEmptyCardioSerie() : makeEmptySerie()],
  };
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

export function makeEmptyDegressif(): DegressifForm {
  return {
    id: generateFormId(),
    poids: null,
    reps: null,
  };
}
