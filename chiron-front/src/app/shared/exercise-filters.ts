/**
 * Filter chips and label maps shared by Bibliotheque and the ProgrammeBuilder's
 * "Add exercise" sheet. Single source of truth for muscle / equipment / difficulty taxonomy.
 */

export interface FilterChip {
  key: string;
  label: string;
}

// Les `label` sont des clés i18n rendues via `| t` aux points d'affichage.
export const MUSCLES: FilterChip[] = [
  { key: 'PECTORAUX',       label: 'muscle.PECTORAUX' },
  { key: 'DOS',             label: 'muscle.DOS' },
  { key: 'EPAULES',         label: 'muscle.EPAULES' },
  { key: 'BICEPS',          label: 'muscle.BICEPS' },
  { key: 'TRICEPS',         label: 'muscle.TRICEPS' },
  { key: 'ABDOMINAUX',      label: 'muscle.ABDOMINAUX' },
  { key: 'QUADRICEPS',      label: 'muscle.QUADRICEPS' },
  { key: 'ISCHIO_JAMBIERS', label: 'muscle.ISCHIO_JAMBIERS' },
  { key: 'FESSIERS',        label: 'muscle.FESSIERS' },
  { key: 'MOLLETS',         label: 'muscle.MOLLETS' },
  { key: 'AVANT_BRAS',      label: 'muscle.AVANT_BRAS' },
  { key: 'TRAPEZES',        label: 'muscle.TRAPEZES' },
  { key: 'LOMBAIRES',       label: 'muscle.LOMBAIRES' },
  { key: 'CARDIO',          label: 'muscle.CARDIO' },
];

export const EQUIPEMENTS: FilterChip[] = [
  { key: 'POIDS_DU_CORPS', label: 'equip.POIDS_DU_CORPS' },
  { key: 'HALTERES',       label: 'equip.HALTERES' },
  { key: 'BARRE',          label: 'equip.BARRE' },
  { key: 'MACHINE',        label: 'equip.MACHINE' },
  { key: 'POULIE',         label: 'equip.POULIE' },
  { key: 'KETTLEBELL',     label: 'equip.KETTLEBELL' },
  { key: 'ELASTIQUE',      label: 'equip.ELASTIQUE' },
];

export const DIFFICULTES: FilterChip[] = [
  { key: 'DEBUTANT',      label: 'diff.DEBUTANT' },
  { key: 'INTERMEDIAIRE', label: 'diff.INTERMEDIAIRE' },
  { key: 'AVANCE',        label: 'diff.AVANCE' },
];

export function muscleLabel(key: string | null | undefined): string {
  if (!key) return '';
  return MUSCLES.find(m => m.key === key)?.label ?? key;
}

export function equipementLabel(key: string | null | undefined): string {
  if (!key) return '';
  return EQUIPEMENTS.find(e => e.key === key)?.label ?? key;
}

export function difficulteLabel(key: string | null | undefined): string {
  if (!key) return '';
  return DIFFICULTES.find(d => d.key === key)?.label ?? key;
}

export function difficulteClass(key: string | null | undefined): string {
  switch (key) {
    case 'DEBUTANT':      return 'text-emerald-400';
    case 'INTERMEDIAIRE': return 'text-amber-400';
    case 'AVANCE':        return 'text-red-400';
    default:              return 'text-on-surface-variant';
  }
}
