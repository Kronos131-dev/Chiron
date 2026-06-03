import { Sexe, BodyCompositionPoint } from '../../service/chiron-api';

/**
 * Configuration des jauges de composition corporelle.
 *
 * Chaque métrique du scan Visbody est décrite ici : sigle, libellé, unité, texte
 * d'explication (bulle) et une fonction `spec()` qui calcule les bornes des trois
 * zones (faible / moyenne / élevée) à partir du profil de l'utilisateur
 * (taille / poids / sexe / âge). Les bornes proviennent de références santé usuelles
 * et restent volontairement centralisées ici pour être ajustables facilement.
 *
 * `spec()` renvoie `null` lorsque le profil ne contient pas les informations
 * nécessaires (taille, sexe…) : la jauge bascule alors en simple valeur affichée.
 */

/** Sens de désirabilité : indique quelle zone est « saine » (colorée en vert). */
export type GaugeDir = 'highGood' | 'lowGood' | 'midGood';

/** Profil utilisateur normalisé pour le calcul des bornes. */
export interface GaugeProfile {
  tailleM: number | null;
  sexe: Sexe | null;
  age: number | null;
  poids: number | null;
}

/** Définition d'une jauge : échelle dessinée + deux frontières + sens. */
export interface GaugeSpec {
  scaleMin: number;
  scaleMax: number;
  b1: number; // frontière faible / moyenne
  b2: number; // frontière moyenne / élevée
  dir: GaugeDir;
}

export interface MetricDef {
  key: keyof BodyCompositionPoint;
  acronym: string;
  label: string;
  unit: string;
  explanation: string;
  spec: (p: GaugeProfile, value: number) => GaugeSpec | null;
}

const isF = (s: Sexe | null): boolean => s === 'FEMME';

/** Métabolisme de base prédit (Mifflin-St Jeor) en kcal/j. */
function predictedBmr(p: GaugeProfile): number | null {
  if (p.poids == null || p.tailleM == null || p.age == null) return null;
  const base = 10 * p.poids + 6.25 * (p.tailleM * 100) - 5 * p.age;
  return isF(p.sexe) ? base - 161 : base + 5;
}

export const COMPOSITION_METRICS: MetricDef[] = [
  {
    key: 'poids',
    acronym: 'Poids',
    label: 'Poids total',
    unit: 'kg',
    explanation:
      "Masse corporelle totale. Les zones correspondent à un IMC sain (18,5 à 25) " +
      'rapporté à ta taille.',
    spec: (p) => {
      if (!p.tailleM) return null;
      const h2 = p.tailleM * p.tailleM;
      const b1 = 18.5 * h2;
      const b2 = 25 * h2;
      return { scaleMin: 15 * h2, scaleMax: 32 * h2, b1, b2, dir: 'midGood' };
    },
  },
  {
    key: 'imc',
    acronym: 'IMC',
    label: 'Indice de masse corporelle',
    unit: '',
    explanation:
      "Rapport poids / taille² (kg/m²). Sous 18,5 : insuffisance pondérale ; " +
      'entre 18,5 et 25 : corpulence normale ; au-dessus de 25 : surpoids.',
    spec: () => ({ scaleMin: 15, scaleMax: 35, b1: 18.5, b2: 25, dir: 'midGood' }),
  },
  {
    key: 'tgcPct',
    acronym: 'TGC',
    label: 'Taux de graisse',
    unit: '%',
    explanation:
      "Pourcentage de masse grasse. Un taux trop bas ou trop élevé n'est pas idéal ; " +
      'la zone saine dépend du sexe et de l’âge.',
    spec: (p) => {
      const ageShift = p.age == null ? 0 : p.age >= 60 ? 3 : p.age >= 40 ? 2 : 0;
      const b1 = (isF(p.sexe) ? 21 : 11) + ageShift;
      const b2 = (isF(p.sexe) ? 33 : 22) + ageShift;
      return { scaleMin: 5, scaleMax: 45, b1, b2, dir: 'midGood' };
    },
  },
  {
    key: 'mgc',
    acronym: 'MG',
    label: 'Masse grasse',
    unit: 'kg',
    explanation:
      'Masse grasse totale en kg. Les zones reflètent un taux de graisse sain ' +
      'appliqué à ton poids mesuré.',
    spec: (p, _v) => {
      if (!p.poids) return null;
      const lowPct = isF(p.sexe) ? 0.21 : 0.11;
      const highPct = isF(p.sexe) ? 0.33 : 0.22;
      const b1 = lowPct * p.poids;
      const b2 = highPct * p.poids;
      return { scaleMin: 0.05 * p.poids, scaleMax: 0.45 * p.poids, b1, b2, dir: 'midGood' };
    },
  },
  {
    key: 'masseMusculaire',
    acronym: 'MM',
    label: 'Masse musculaire',
    unit: 'kg',
    explanation:
      'Masse maigre « molle » mesurée par le scan (muscle + eau, hors os). Elle représente ' +
      'environ 75-85 % du poids ; plus elle est élevée à poids égal, mieux c’est.',
    spec: (p) => {
      if (!p.poids) return null;
      const b1 = (isF(p.sexe) ? 0.64 : 0.72) * p.poids;
      const b2 = (isF(p.sexe) ? 0.76 : 0.81) * p.poids;
      const min = (isF(p.sexe) ? 0.48 : 0.55) * p.poids;
      const max = (isF(p.sexe) ? 0.88 : 0.92) * p.poids;
      return { scaleMin: min, scaleMax: max, b1, b2, dir: 'highGood' };
    },
  },
  {
    key: 'mms',
    acronym: 'MMS',
    label: 'Muscle squelettique',
    unit: 'kg',
    explanation:
      'Masse musculaire squelettique (les muscles que tu entraînes). Plus elle est élevée ' +
      'à poids égal, mieux c’est. Zones : homme sain 33-39 % du poids (athlétique > 39 %) ; ' +
      'femme 24-30 % (> 30 %).',
    spec: (p) => {
      if (!p.poids) return null;
      const b1 = (isF(p.sexe) ? 0.24 : 0.33) * p.poids;
      const b2 = (isF(p.sexe) ? 0.30 : 0.39) * p.poids;
      const min = (isF(p.sexe) ? 0.16 : 0.22) * p.poids;
      const max = (isF(p.sexe) ? 0.45 : 0.55) * p.poids;
      return { scaleMin: min, scaleMax: max, b1, b2, dir: 'highGood' };
    },
  },
  {
    key: 'mmc',
    acronym: 'MMaigre',
    label: 'Masse maigre',
    unit: 'kg',
    explanation:
      'Masse non grasse (muscles, os, eau, organes). Plus elle est élevée, mieux c’est. ' +
      'Zones : homme sain 75-84 % du poids (athlétique > 84 %) ; femme 67-79 % (> 79 %).',
    spec: (p) => {
      if (!p.poids) return null;
      const b1 = (isF(p.sexe) ? 0.67 : 0.75) * p.poids;
      const b2 = (isF(p.sexe) ? 0.79 : 0.84) * p.poids;
      const min = (isF(p.sexe) ? 0.55 : 0.60) * p.poids;
      const max = (isF(p.sexe) ? 0.90 : 0.95) * p.poids;
      return { scaleMin: min, scaleMax: max, b1, b2, dir: 'highGood' };
    },
  },
  {
    key: 'graisseViscerale',
    acronym: 'GV',
    label: 'Graisse viscérale',
    unit: '',
    explanation:
      "Graisse autour des organes abdominaux. Indice de 1 à 9 : sain ; 10 à 14 : élevé ; " +
      '15 et plus : risque important. Plus c’est bas, mieux c’est.',
    spec: () => ({ scaleMin: 1, scaleMax: 20, b1: 9, b2: 14, dir: 'lowGood' }),
  },
  {
    key: 'eauTotale',
    acronym: 'Eau',
    label: 'Eau totale',
    unit: 'kg',
    explanation:
      "Eau corporelle totale. Une bonne hydratation se situe autour de 50-65 % du poids " +
      'chez l’homme et 45-60 % chez la femme.',
    spec: (p) => {
      if (!p.poids) return null;
      const lowPct = isF(p.sexe) ? 0.45 : 0.50;
      const highPct = isF(p.sexe) ? 0.60 : 0.65;
      const b1 = lowPct * p.poids;
      const b2 = highPct * p.poids;
      return { scaleMin: 0.35 * p.poids, scaleMax: 0.72 * p.poids, b1, b2, dir: 'midGood' };
    },
  },
  {
    key: 'eauIntra',
    acronym: 'ICW',
    label: 'Eau intracellulaire',
    unit: 'kg',
    explanation:
      "Eau contenue dans les cellules (≈ 60 % de l’eau corporelle). Liée à la masse " +
      'musculaire : plus elle est élevée, mieux c’est.',
    spec: (p) => {
      if (!p.poids) return null;
      return { scaleMin: 0.2 * p.poids, scaleMax: 0.48 * p.poids, b1: 0.30 * p.poids, b2: 0.40 * p.poids, dir: 'highGood' };
    },
  },
  {
    key: 'eauExtra',
    acronym: 'ECW',
    label: 'Eau extracellulaire',
    unit: 'kg',
    explanation:
      "Eau hors des cellules (sang, lymphe…). Une part trop élevée peut signaler une " +
      'rétention d’eau. Zone moyenne = équilibre normal.',
    spec: (p) => {
      if (!p.poids) return null;
      return { scaleMin: 0.1 * p.poids, scaleMax: 0.32 * p.poids, b1: 0.17 * p.poids, b2: 0.25 * p.poids, dir: 'midGood' };
    },
  },
  {
    key: 'ratioEcwTbw',
    acronym: 'ECW/TBW',
    label: 'Ratio eau extra / totale',
    unit: '',
    explanation:
      "Équilibre hydrique. Normal autour de 0,36-0,39. Au-delà de 0,40 : rétention d’eau " +
      'ou inflammation possible. Plus c’est bas (dans la norme), mieux c’est.',
    spec: () => ({ scaleMin: 0.30, scaleMax: 0.45, b1: 0.38, b2: 0.40, dir: 'lowGood' }),
  },
  {
    key: 'masseProteine',
    acronym: 'Prot.',
    label: 'Masse protéique',
    unit: 'kg',
    explanation:
      'Protéines corporelles (surtout musculaires). Une valeur élevée, proportionnée au ' +
      'poids, traduit une bonne masse musculaire.',
    spec: (p) => {
      if (!p.poids) return null;
      return { scaleMin: 0.08 * p.poids, scaleMax: 0.24 * p.poids, b1: 0.15 * p.poids, b2: 0.19 * p.poids, dir: 'highGood' };
    },
  },
  {
    key: 'selInorganique',
    acronym: 'Sels',
    label: 'Minéraux (sels)',
    unit: 'kg',
    explanation:
      'Sels minéraux du corps (dont les os). Représentent environ 3,5 à 5 % du poids. ' +
      'Zone moyenne = valeur attendue.',
    spec: (p) => {
      if (!p.poids) return null;
      return { scaleMin: 0.02 * p.poids, scaleMax: 0.07 * p.poids, b1: 0.035 * p.poids, b2: 0.05 * p.poids, dir: 'midGood' };
    },
  },
  {
    key: 'mbKcal',
    acronym: 'MB',
    label: 'Métabolisme de base',
    unit: 'kcal',
    explanation:
      "Énergie dépensée au repos par jour. La zone moyenne correspond à ton métabolisme " +
      'attendu (formule Mifflin-St Jeor) ; au-dessus = métabolisme plus actif.',
    spec: (p) => {
      const pred = predictedBmr(p);
      if (pred == null) return null;
      return { scaleMin: 0.6 * pred, scaleMax: 1.4 * pred, b1: 0.9 * pred, b2: 1.1 * pred, dir: 'midGood' };
    },
  },
  {
    key: 'ageMetabolique',
    acronym: 'Âge méta',
    label: 'Âge métabolique',
    unit: 'ans',
    explanation:
      "Âge correspondant à ton métabolisme. Inférieur à ton âge réel : bon signe ; " +
      'supérieur : métabolisme à améliorer.',
    spec: (p) => {
      if (p.age == null) return null;
      return { scaleMin: Math.max(10, p.age - 15), scaleMax: p.age + 20, b1: p.age - 5, b2: p.age + 5, dir: 'lowGood' };
    },
  },
  {
    key: 'rth',
    acronym: 'RTH',
    label: 'Ratio taille / hanche',
    unit: '',
    explanation:
      "Rapport tour de taille / tour de hanches. Indicateur de répartition des graisses. " +
      'Plus c’est bas, plus le risque cardio-métabolique est faible.',
    spec: (p) => {
      const b1 = isF(p.sexe) ? 0.80 : 0.90;
      const b2 = isF(p.sexe) ? 0.85 : 0.95;
      return { scaleMin: 0.70, scaleMax: 1.10, b1, b2, dir: 'lowGood' };
    },
  },
];
