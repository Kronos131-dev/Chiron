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
}

export interface ExerciceForm {
  id: number | string;
  nom: string;
  definitionId?: number;
  series: SerieForm[];
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
export function makeEmptyExercice(nom: string = '', definitionId?: number): ExerciceForm {
  return {
    id: generateFormId(),
    nom,
    definitionId,
    series: [makeEmptySerie()],
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

export function makeEmptyDegressif(): DegressifForm {
  return {
    id: generateFormId(),
    poids: null,
    reps: null,
  };
}
