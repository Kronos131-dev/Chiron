/**
 * Filter chips and label maps shared by Bibliotheque and the ProgrammeBuilder's
 * "Add exercise" sheet. Single source of truth for muscle / equipment / difficulty taxonomy.
 */

export interface FilterChip {
  key: string;
  label: string;
}

export const MUSCLES: FilterChip[] = [
  { key: 'PECTORAUX',       label: 'Pectoraux' },
  { key: 'DOS',             label: 'Dos' },
  { key: 'EPAULES',         label: 'Épaules' },
  { key: 'BICEPS',          label: 'Biceps' },
  { key: 'TRICEPS',         label: 'Triceps' },
  { key: 'ABDOMINAUX',      label: 'Abdos' },
  { key: 'QUADRICEPS',      label: 'Quadriceps' },
  { key: 'ISCHIO_JAMBIERS', label: 'Ischio-jamb.' },
  { key: 'FESSIERS',        label: 'Fessiers' },
  { key: 'MOLLETS',         label: 'Mollets' },
  { key: 'AVANT_BRAS',      label: 'Avant-bras' },
  { key: 'TRAPEZES',        label: 'Trapèzes' },
  { key: 'LOMBAIRES',       label: 'Lombaires' },
];

export const EQUIPEMENTS: FilterChip[] = [
  { key: 'POIDS_DU_CORPS', label: 'Poids du corps' },
  { key: 'HALTERES',       label: 'Haltères' },
  { key: 'BARRE',          label: 'Barre' },
  { key: 'MACHINE',        label: 'Machine' },
  { key: 'POULIE',         label: 'Poulie' },
  { key: 'KETTLEBELL',     label: 'Kettlebell' },
  { key: 'ELASTIQUE',      label: 'Élastique' },
];

export const DIFFICULTES: FilterChip[] = [
  { key: 'DEBUTANT',      label: 'Débutant' },
  { key: 'INTERMEDIAIRE', label: 'Intermédiaire' },
  { key: 'AVANCE',        label: 'Avancé' },
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
