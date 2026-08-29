import { CoursePointDto } from '../service/chiron-api';
import { allureKmh, distanceHaversineM } from '../service/course-tracker';

export interface RepereKm {
  x: number;
  km: number;
}

export interface GrapheAllure {
  chemin: string;
  aire: string;
  reperes: RepereKm[];
  largeur: number;
  hauteur: number;
  allureLenteKmh: number;
  allureRapideKmh: number;
  distanceM: number;
}

interface Jalon {
  distanceM: number;
  tCourseMs: number;
}

const MS_PAR_SECONDE = 1000;
const KM_EN_METRES = 1000;
const FENETRE_M = 150;
const ECHANTILLONS = 140;
const DISTANCE_MIN_M = 100;
const MARGE_ECHELLE = 0.08;
const PERCENTILE_BAS = 0.05;
const PERCENTILE_HAUT = 0.95;

function jalonner(points: CoursePointDto[]): Jalon[] {
  const jalons: Jalon[] = [{ distanceM: 0, tCourseMs: 0 }];
  const depart = points[0].t;
  let cumulM = 0;
  let pauseCumuleeMs = 0;

  for (let i = 1; i < points.length; i++) {
    const precedent = points[i - 1];
    const courant = points[i];
    if (courant.coupure) pauseCumuleeMs += courant.t - precedent.t;
    else cumulM += distanceHaversineM(precedent, courant);
    jalons.push({ distanceM: cumulM, tCourseMs: courant.t - depart - pauseCumuleeMs });
  }
  return jalons;
}

// WHY: l'allure d'un point au suivant saute de 8 à 16 km/h sous les arbres, comme dans la
// fenêtre glissante de la course. Sur cent cinquante mètres le trait redevient la courbe que
// l'athlète reconnaît de sa sortie, au lieu d'une pelote de bruit GPS.
function vitesseGlissante(jalons: Jalon[], index: number): number {
  const fin = jalons[index];
  let debut = jalons[0];
  for (let i = index; i >= 0; i--) {
    debut = jalons[i];
    if (fin.distanceM - jalons[i].distanceM >= FENETRE_M) break;
  }
  const dureeS = (fin.tCourseMs - debut.tCourseMs) / MS_PAR_SECONDE;
  return allureKmh(fin.distanceM - debut.distanceM, Math.round(dureeS));
}

function indexALaDistance(jalons: Jalon[], distanceM: number, depuis: number): number {
  let index = depuis;
  while (index < jalons.length - 1 && jalons[index].distanceM < distanceM) index++;
  return index;
}

// WHY: un seul point aberrant à 40 km/h écraserait toute la course dans le bas du cadre. Les
// bornes viennent des centiles, pas des extrêmes : la courbe garde alors son relief, et la
// pointe de GPS sort simplement du cadre.
function bornes(vitesses: number[]): { lente: number; rapide: number } {
  const valides = vitesses.filter((v) => v > 0).sort((a, b) => a - b);
  if (!valides.length) return { lente: 0, rapide: 1 };
  const basse = valides[Math.floor(PERCENTILE_BAS * (valides.length - 1))];
  const haute = valides[Math.ceil(PERCENTILE_HAUT * (valides.length - 1))];
  const marge = Math.max((haute - basse) * MARGE_ECHELLE, 0.2);
  return { lente: Math.max(0, basse - marge), rapide: haute + marge };
}

export function construireGrapheAllure(
  points: CoursePointDto[],
  largeur: number,
  hauteur: number,
): GrapheAllure | null {
  if (points.length < 2) return null;

  const jalons = jalonner(points);
  const distanceM = jalons[jalons.length - 1].distanceM;
  if (distanceM < DISTANCE_MIN_M) return null;

  const vitesses: number[] = [];
  const abscisses: number[] = [];
  let curseur = 0;

  for (let i = 0; i < ECHANTILLONS; i++) {
    const distance = (i / (ECHANTILLONS - 1)) * distanceM;
    curseur = indexALaDistance(jalons, distance, curseur);
    vitesses.push(vitesseGlissante(jalons, curseur));
    abscisses.push((distance / distanceM) * largeur);
  }

  const { lente, rapide } = bornes(vitesses);
  const etendue = Math.max(rapide - lente, 0.1);
  const y = (kmh: number) => {
    const ratio = Math.min(1, Math.max(0, (kmh - lente) / etendue));
    return hauteur - ratio * hauteur;
  };

  let chemin = '';
  for (let i = 0; i < vitesses.length; i++) {
    const ordonnee = y(vitesses[i]).toFixed(2);
    chemin += `${i === 0 ? 'M' : ' L'} ${abscisses[i].toFixed(2)} ${ordonnee}`;
  }

  const reperes: RepereKm[] = [];
  for (let km = 1; km * KM_EN_METRES < distanceM; km++) {
    reperes.push({ km, x: ((km * KM_EN_METRES) / distanceM) * largeur });
  }

  return {
    chemin,
    aire: `${chemin} L ${largeur.toFixed(2)} ${hauteur.toFixed(2)} L 0 ${hauteur.toFixed(2)} Z`,
    reperes,
    largeur,
    hauteur,
    allureLenteKmh: lente,
    allureRapideKmh: rapide,
    distanceM,
  };
}
