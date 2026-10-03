import { WodType } from './exercise-forms';

export interface WodSpec {
  dureeMin: number;
  mouvements: string[];
  repsParTour: number[];
  annoncesMin: number[];
}

export const WOD_SPECS: Record<WodType, WodSpec> = {
  CINDY: {
    dureeMin: 20,
    mouvements: ['wod.cindy.pullups', 'wod.cindy.pushups', 'wod.cindy.squats'],
    repsParTour: [5, 10, 15],
    annoncesMin: [15, 10, 5],
  },
};

export function wodSpec(type: WodType | null | undefined): WodSpec | null {
  return type ? (WOD_SPECS[type] ?? null) : null;
}
