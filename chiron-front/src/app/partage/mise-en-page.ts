export type NomFormat = 'portrait' | 'story';

export interface Format {
  nom: NomFormat;
  largeur: number;
  hauteur: number;
  margeVerticale: number;
  margeHorizontale: number;
  lignesMax: number;
  traceHauteurMin: number;
}

export const PORTRAIT: Format = {
  nom: 'portrait',
  largeur: 1080,
  hauteur: 1350,
  margeVerticale: 48,
  margeHorizontale: 48,
  lignesMax: 6,
  traceHauteurMin: 340,
};

export const STORY: Format = {
  nom: 'story',
  largeur: 1080,
  hauteur: 1920,
  margeVerticale: 220,
  margeHorizontale: 48,
  lignesMax: 9,
  traceHauteurMin: 600,
};

export const FORMATS: Record<NomFormat, Format> = { portrait: PORTRAIT, story: STORY };

export interface Zone {
  x: number;
  y: number;
  largeur: number;
  hauteur: number;
}

const HAUTEUR_ENTETE = 150;
const HAUTEUR_PIED = 150;
const ECART_PIED = 24;
const MARGE_PANNEAU = 56;

export interface Cadre {
  entete: Zone;
  panneau: Zone;
  contenu: Zone;
  pied: Zone;
}

export function cadreDe(format: Format): Cadre {
  const x = format.margeHorizontale;
  const largeur = format.largeur - 2 * x;
  const haut = format.margeVerticale;
  const piedY = format.hauteur - format.margeVerticale - HAUTEUR_PIED;
  const panneauY = haut + HAUTEUR_ENTETE;
  const panneauH = piedY - ECART_PIED - panneauY;
  return {
    entete: { x, y: haut, largeur, hauteur: HAUTEUR_ENTETE },
    panneau: { x, y: panneauY, largeur, hauteur: panneauH },
    contenu: {
      x: x + MARGE_PANNEAU,
      y: panneauY + MARGE_PANNEAU,
      largeur: largeur - 2 * MARGE_PANNEAU,
      hauteur: panneauH - 2 * MARGE_PANNEAU,
    },
    pied: { x, y: piedY, largeur, hauteur: HAUTEUR_PIED },
  };
}

export type Mesure = (texte: string, taille: number) => number;

export function tailleAjustee(
  texte: string,
  mesure: Mesure,
  largeurMax: number,
  tailleMax: number,
  tailleMin: number,
): number {
  let taille = tailleMax;
  while (taille > tailleMin && mesure(texte, taille) > largeurMax) taille -= 2;
  return Math.max(taille, tailleMin);
}

const POINTS_DE_SUSPENSION = '…';

function tronquer(texte: string, mesure: (s: string) => number, largeurMax: number): string {
  let coupe = texte;
  while (coupe.length > 1 && mesure(coupe + POINTS_DE_SUSPENSION) > largeurMax) {
    coupe = coupe.slice(0, -1);
  }
  return coupe.trimEnd() + POINTS_DE_SUSPENSION;
}

export function decouperEnLignes(
  texte: string,
  mesure: (s: string) => number,
  largeurMax: number,
  lignesMax: number,
): string[] {
  const mots = texte.split(/\s+/).filter(Boolean);
  const lignes: string[] = [];
  let courante = '';
  for (let index = 0; index < mots.length; index++) {
    const essai = courante ? `${courante} ${mots[index]}` : mots[index];
    if (mesure(essai) <= largeurMax || !courante) {
      courante = essai;
      continue;
    }
    lignes.push(courante);
    courante = mots[index];
    if (lignes.length === lignesMax - 1) {
      courante = mots.slice(index).join(' ');
      break;
    }
  }
  if (courante) lignes.push(courante);
  const dernier = lignes.length - 1;
  if (dernier >= 0 && mesure(lignes[dernier]) > largeurMax) {
    lignes[dernier] = tronquer(lignes[dernier], mesure, largeurMax);
  }
  return lignes;
}

export interface Troncature<T> {
  visibles: T[];
  masquees: number;
}

export function tronquerLignes<T>(
  lignes: T[],
  placeDisponible: number,
  hauteurLigne: number,
  maximum: number,
  hauteurSuite: number,
): Troncature<T> {
  const parHauteur = Math.max(0, Math.floor(placeDisponible / hauteurLigne));
  if (lignes.length <= Math.min(maximum, parHauteur)) return { visibles: lignes, masquees: 0 };
  const avecSuite = Math.max(0, Math.floor((placeDisponible - hauteurSuite) / hauteurLigne));
  const visibles = Math.min(maximum, avecSuite, lignes.length - 1);
  return { visibles: lignes.slice(0, visibles), masquees: lignes.length - visibles };
}

export const SPLITS_MAX_BARRES = 21;

export function regrouperSplits(valeurs: number[], maximum: number = SPLITS_MAX_BARRES): number[] {
  if (valeurs.length <= maximum) return valeurs;
  const taille = Math.ceil(valeurs.length / maximum);
  const groupes: number[] = [];
  for (let debut = 0; debut < valeurs.length; debut += taille) {
    const tranche = valeurs.slice(debut, debut + taille);
    groupes.push(tranche.reduce((somme, valeur) => somme + valeur, 0) / tranche.length);
  }
  return groupes;
}
