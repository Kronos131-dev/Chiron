export type Univers = 'novice' | 'athlete' | 'legend';

export type ArretMetal = readonly [position: number, couleur: string];

export interface Palier {
  niveau: number;
  couleur: string;
  accent: string;
  bordure: string;
  halo: string;
  metal: readonly ArretMetal[];
  univers: Univers;
  lueur: string | null;
}

const BRONZE: Palier = {
  niveau: 0,
  couleur: '#ffb779',
  accent: '#ffd9b0',
  bordure: 'rgba(255, 183, 121, 0.4)',
  halo: 'rgba(255, 183, 121, 0.12)',
  metal: [
    [0, '#7a4a1c'],
    [0.46, '#ffb779'],
    [0.54, '#ffd9b0'],
    [1, '#a8672a'],
  ],
  univers: 'novice',
  lueur: null,
};

const PALIERS: Record<number, Palier> = {
  1: {
    niveau: 1,
    couleur: '#9c7c4f',
    accent: '#c2a06a',
    bordure: 'rgba(156, 124, 79, 0.38)',
    halo: 'rgba(156, 124, 79, 0.12)',
    metal: [
      [0, '#4f3d22'],
      [0.46, '#8a6c42'],
      [0.54, '#b89360'],
      [1, '#6b5230'],
    ],
    univers: 'novice',
    lueur: null,
  },
  2: {
    niveau: 2,
    couleur: '#9aa3ab',
    accent: '#cdd4da',
    bordure: 'rgba(154, 163, 171, 0.4)',
    halo: 'rgba(154, 163, 171, 0.12)',
    metal: [
      [0, '#5b636b'],
      [0.46, '#98a1a9'],
      [0.54, '#cdd4da'],
      [1, '#717a82'],
    ],
    univers: 'novice',
    lueur: null,
  },
  3: {
    niveau: 3,
    couleur: '#a86a55',
    accent: '#c98e76',
    bordure: 'rgba(168, 106, 85, 0.42)',
    halo: 'rgba(168, 106, 85, 0.13)',
    metal: [
      [0, '#5a3026'],
      [0.46, '#93503e'],
      [0.54, '#be7259'],
      [1, '#6c3a2c'],
    ],
    univers: 'athlete',
    lueur: null,
  },
  4: {
    niveau: 4,
    couleur: '#a35260',
    accent: '#c47e8c',
    bordure: 'rgba(140, 66, 80, 0.44)',
    halo: 'rgba(140, 66, 80, 0.15)',
    metal: [
      [0, '#4d222c'],
      [0.46, '#82394a'],
      [0.54, '#ab5a6c'],
      [1, '#5d2935'],
    ],
    univers: 'athlete',
    lueur: null,
  },
  5: {
    niveau: 5,
    couleur: '#8472a6',
    accent: '#a99cc4',
    bordure: 'rgba(132, 114, 166, 0.46)',
    halo: 'rgba(132, 114, 166, 0.15)',
    metal: [
      [0, '#3c3055'],
      [0.46, '#6e5f93'],
      [0.54, '#9384b4'],
      [1, '#463a63'],
    ],
    univers: 'athlete',
    lueur: 'rgba(132, 114, 166, 0.35)',
  },
  6: {
    niveau: 6,
    couleur: '#b0904a',
    accent: '#d0b06a',
    bordure: 'rgba(176, 144, 74, 0.44)',
    halo: 'rgba(176, 144, 74, 0.14)',
    metal: [
      [0, '#5f4716'],
      [0.46, '#9a7a36'],
      [0.54, '#c4a25a'],
      [1, '#6f5420'],
    ],
    univers: 'legend',
    lueur: null,
  },
  7: {
    niveau: 7,
    couleur: '#c6a24e',
    accent: '#e2c277',
    bordure: 'rgba(198, 162, 78, 0.5)',
    halo: 'rgba(198, 162, 78, 0.16)',
    metal: [
      [0, '#6f521a'],
      [0.46, '#b08e3c'],
      [0.54, '#d8b765'],
      [1, '#7e5f22'],
    ],
    univers: 'legend',
    lueur: 'rgba(198, 162, 78, 0.4)',
  },
  8: {
    niveau: 8,
    couleur: '#d4b86a',
    accent: '#efe6cf',
    bordure: 'rgba(212, 184, 106, 0.56)',
    halo: 'rgba(212, 184, 106, 0.18)',
    metal: [
      [0, '#7c5f24'],
      [0.42, '#c4a455'],
      [0.54, '#e8d59a'],
      [0.62, '#efe6cf'],
      [1, '#8a6c2c'],
    ],
    univers: 'legend',
    lueur: 'rgba(212, 184, 106, 0.5)',
  },
};

export const NIVEAU_MAX = 8;

export function palierDe(niveau: number | null | undefined): Palier {
  const borne = Math.round(Number(niveau));
  if (!Number.isFinite(borne) || borne < 1) return BRONZE;
  return PALIERS[Math.min(NIVEAU_MAX, borne)];
}
