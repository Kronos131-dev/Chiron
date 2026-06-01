/**
 * Estimation du 1RM côté client — miroir exact du backend OneRepMaxEstimator.
 *
 * La formule dépend de l'exercice (biomécanique), et les reps sont plafonnées à MAX_REPS :
 * au-delà, l'endurance musculaire fausse toute formule. Pour les mouvements lestés, on estime
 * le 1RM total (poids de corps + lest) puis on en déduit le 1RM lesté affiché (total − PC).
 */
export type RmFormula = 'EPLEY' | 'BRZYCKI';

export const MAX_REPS = 10;

const FORMULA: Record<string, RmFormula> = {
  DEVELOPPE_COUCHE: 'BRZYCKI',
  SQUAT:            'EPLEY',
  SOULEVE_DE_TERRE: 'EPLEY',
  TRACTIONS:        'BRZYCKI',
  DIPS:             'BRZYCKI',
};

const BODYWEIGHT: Record<string, boolean> = {
  TRACTIONS: true,
  DIPS:      true,
};

export function isBodyweight(type: string): boolean {
  return BODYWEIGHT[type] ?? false;
}

function clampReps(reps: number): number {
  return Math.min(Math.max(reps, 1), MAX_REPS);
}

function apply(formula: RmFormula, load: number, reps: number): number {
  return formula === 'EPLEY' ? load * (1 + reps / 30) : load * (36 / (37 - reps));
}

/** 1RM total (charge effective = poids de corps + lest pour les mouvements lestés). */
export function total1RM(type: string, poids: number, reps: number, poidsCorps: number | null): number {
  const r = clampReps(reps);
  const load = (isBodyweight(type) && poidsCorps) ? poids + poidsCorps : poids;
  return apply(FORMULA[type] ?? 'EPLEY', load, r);
}

/** 1RM affiché : charge lestée seule (total − PC) pour les mouvements lestés, total sinon. */
export function display1RM(type: string, poids: number, reps: number, poidsCorps: number | null): number {
  if (isBodyweight(type)) {
    if (!poidsCorps) return apply('EPLEY', poids, clampReps(reps));
    return total1RM(type, poids, reps, poidsCorps) - poidsCorps;
  }
  return total1RM(type, poids, reps, poidsCorps);
}

/** Ratio de performance pour le palier : 1RM total / poids de corps. */
export function ratio1RM(type: string, poids: number, reps: number, poidsCorps: number): number {
  return total1RM(type, poids, reps, poidsCorps) / poidsCorps;
}
