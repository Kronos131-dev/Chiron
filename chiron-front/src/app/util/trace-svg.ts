import { CoursePointDto } from '../service/chiron-api';
import { allureKmh, distanceHaversineM } from '../service/course-tracker';

export interface SegmentTrace {
  d: string;
  allureKmh: number;
}

export interface TraceSvg {
  segments: SegmentTrace[];
  largeur: number;
  hauteur: number;
  allureMinKmh: number;
  allureMaxKmh: number;
}

const MARGE = 6;
const MS_PAR_SECONDE = 1000;
const TRACE_VIDE: TraceSvg = {
  segments: [],
  largeur: 0,
  hauteur: 0,
  allureMinKmh: 0,
  allureMaxKmh: 0,
};

// WHY: à l'échelle d'une sortie à pied, une projection équirectangulaire corrigée du cosinus de
// la latitude est fidèle au mètre près. Elle évite d'embarquer une bibliothèque de cartographie
// et un fond de carte, donc de dépendre du réseau au moment précis où l'athlète n'en a pas.
export function projeterTrace(points: CoursePointDto[], cote: number): TraceSvg {
  if (points.length < 2) return TRACE_VIDE;

  const lats = points.map((p) => p.lat);
  const lons = points.map((p) => p.lon);
  const latMin = Math.min(...lats);
  const latMax = Math.max(...lats);
  const lonMin = Math.min(...lons);
  const lonMax = Math.max(...lons);

  const cosLat = Math.cos(((latMin + latMax) / 2) * (Math.PI / 180));
  const etendueX = Math.max((lonMax - lonMin) * cosLat, 1e-9);
  const etendueY = Math.max(latMax - latMin, 1e-9);

  const utile = cote - 2 * MARGE;
  const echelle = Math.min(utile / etendueX, utile / etendueY);

  // WHY: le cadre reste carré et le tracé est centré dedans. Épouser les proportions du
  // parcours donnait un viewBox de 12 sur 320 pour un aller-retour en ligne droite, que le
  // `w-full` de la page étirait ensuite sur plusieurs écrans de haut.
  const decalageX = (cote - etendueX * echelle) / 2;
  const decalageY = (cote - etendueY * echelle) / 2;

  const x = (lon: number) => decalageX + (lon - lonMin) * cosLat * echelle;
  const y = (lat: number) => cote - decalageY - (lat - latMin) * echelle;

  const segments: SegmentTrace[] = [];
  let allureMinKmh = Number.POSITIVE_INFINITY;
  let allureMaxKmh = 0;

  for (let i = 1; i < points.length; i++) {
    const precedent = points[i - 1];
    const courant = points[i];
    if (courant.coupure) continue;

    const dureeS = Math.round((courant.t - precedent.t) / MS_PAR_SECONDE);
    const vitesse = allureKmh(distanceHaversineM(precedent, courant), dureeS);
    if (vitesse > 0) {
      allureMinKmh = Math.min(allureMinKmh, vitesse);
      allureMaxKmh = Math.max(allureMaxKmh, vitesse);
    }

    segments.push({
      d: `M ${x(precedent.lon).toFixed(2)} ${y(precedent.lat).toFixed(2)} L ${x(courant.lon).toFixed(2)} ${y(courant.lat).toFixed(2)}`,
      allureKmh: vitesse,
    });
  }

  if (!segments.length) return TRACE_VIDE;

  return {
    segments,
    largeur: cote,
    hauteur: cote,
    allureMinKmh: Number.isFinite(allureMinKmh) ? allureMinKmh : 0,
    allureMaxKmh,
  };
}

export interface PointProjete {
  x: number;
  y: number;
}

export interface SegmentProjete {
  de: PointProjete;
  vers: PointProjete;
  allureKmh: number;
}

export interface TraceProjetee {
  segments: SegmentProjete[];
  depart: PointProjete | null;
  arrivee: PointProjete | null;
  allureMinKmh: number;
  allureMaxKmh: number;
}

const TRACE_PROJETEE_VIDE: TraceProjetee = {
  segments: [],
  depart: null,
  arrivee: null,
  allureMinKmh: 0,
  allureMaxKmh: 0,
};

export function projeterPointsDans(
  points: CoursePointDto[],
  largeur: number,
  hauteur: number,
  marge: number,
): TraceProjetee {
  if (points.length < 2) return TRACE_PROJETEE_VIDE;

  const lats = points.map((p) => p.lat);
  const lons = points.map((p) => p.lon);
  const latMin = Math.min(...lats);
  const latMax = Math.max(...lats);
  const lonMin = Math.min(...lons);
  const lonMax = Math.max(...lons);

  const cosLat = Math.cos(((latMin + latMax) / 2) * (Math.PI / 180));
  const etendueX = Math.max((lonMax - lonMin) * cosLat, 1e-9);
  const etendueY = Math.max(latMax - latMin, 1e-9);

  const echelle = Math.min((largeur - 2 * marge) / etendueX, (hauteur - 2 * marge) / etendueY);
  const decalageX = (largeur - etendueX * echelle) / 2;
  const decalageY = (hauteur - etendueY * echelle) / 2;

  const projeter = (point: CoursePointDto): PointProjete => ({
    x: decalageX + (point.lon - lonMin) * cosLat * echelle,
    y: hauteur - decalageY - (point.lat - latMin) * echelle,
  });

  const segments: SegmentProjete[] = [];
  let allureMinKmh = Number.POSITIVE_INFINITY;
  let allureMaxKmh = 0;

  for (let i = 1; i < points.length; i++) {
    const precedent = points[i - 1];
    const courant = points[i];
    if (courant.coupure) continue;

    const dureeS = Math.round((courant.t - precedent.t) / MS_PAR_SECONDE);
    const vitesse = allureKmh(distanceHaversineM(precedent, courant), dureeS);
    if (vitesse > 0) {
      allureMinKmh = Math.min(allureMinKmh, vitesse);
      allureMaxKmh = Math.max(allureMaxKmh, vitesse);
    }
    segments.push({ de: projeter(precedent), vers: projeter(courant), allureKmh: vitesse });
  }

  if (!segments.length) return TRACE_PROJETEE_VIDE;

  return {
    segments,
    depart: projeter(points[0]),
    arrivee: projeter(points[points.length - 1]),
    allureMinKmh: Number.isFinite(allureMinKmh) ? allureMinKmh : 0,
    allureMaxKmh,
  };
}
