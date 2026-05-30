export interface Tier {
  level: number;
  name: string;
  cat: 'Novice' | 'Athlète' | 'Légende';
}

export const TIERS: Tier[] = [
  { level: 1, name: 'Éphèbe',    cat: 'Novice'  },
  { level: 2, name: 'Argonaute', cat: 'Novice'  },
  { level: 3, name: 'Hoplite',   cat: 'Athlète' },
  { level: 4, name: 'Myrmidon',  cat: 'Athlète' },
  { level: 5, name: 'Spartiate', cat: 'Athlète' },
  { level: 6, name: 'Héros',     cat: 'Légende' },
  { level: 7, name: 'Demi-Dieu', cat: 'Légende' },
  { level: 8, name: 'Olympien',  cat: 'Légende' },
];
