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

export type BlockType = 'SUPERSET' | 'BISET';

export interface ExerciceForm {
  id: number | string;
  nom: string;
  definitionId?: number;
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
