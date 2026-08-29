import { describe, expect, it } from 'vitest';
import { CoursePointDto } from '../service/chiron-api';
import { construireGrapheAllure } from './graphe-allure';

const LAT_DEPART = 48.8566;
const LON_DEPART = 2.3522;
const T_DEPART = 1_700_000_000_000;
const DEGRE_LATITUDE_EN_METRES = (2 * Math.PI * 6371008.8) / 360;

function point(metres: number, secondes: number, coupure = false): CoursePointDto {
  return {
    lat: LAT_DEPART + metres / DEGRE_LATITUDE_EN_METRES,
    lon: LON_DEPART,
    t: T_DEPART + secondes * 1000,
    alt: null,
    coupure,
  };
}

function ordonnees(chemin: string): number[] {
  return chemin
    .trim()
    .split(/[ML]/)
    .map((paire) => paire.trim())
    .filter(Boolean)
    .map((paire) => Number.parseFloat(paire.split(/\s+/)[1]));
}

// WHY: 100 m toutes les `secondesParCent` secondes — c'est la seule façon de fabriquer une
// allure connue d'avance et donc de vérifier ce que la courbe raconte.
function segment(depuisM: number, jusquaM: number, depuisS: number, secondesParCent: number) {
  const points: CoursePointDto[] = [];
  let secondes = depuisS;
  for (let metres = depuisM + 100; metres <= jusquaM; metres += 100) {
    secondes += secondesParCent;
    points.push(point(metres, secondes));
  }
  return { points, finS: secondes };
}

describe('construireGrapheAllure', () => {
  it('ne rend rien sous deux points', () => {
    expect(construireGrapheAllure([], 320, 120)).toBeNull();
    expect(construireGrapheAllure([point(0, 0)], 320, 120)).toBeNull();
  });

  it('ne rend rien sur une sortie de quelques dizaines de mètres', () => {
    expect(construireGrapheAllure([point(0, 0), point(40, 20)], 320, 120)).toBeNull();
  });

  it('pose un repère par kilomètre franchi', () => {
    const { points } = segment(0, 2500, 0, 30);
    const graphe = construireGrapheAllure([point(0, 0), ...points], 320, 120)!;

    expect(graphe.reperes.map((r) => r.km)).toEqual([1, 2]);
    expect(graphe.reperes[0].x).toBeCloseTo((1000 / graphe.distanceM) * 320, 1);
    expect(graphe.distanceM).toBeCloseTo(2500, 0);
  });

  it('dessine la partie rapide plus haut que la partie lente', () => {
    const rapide = segment(0, 1000, 0, 20);
    const lent = segment(1000, 2000, rapide.finS, 60);
    const graphe = construireGrapheAllure(
      [point(0, 0), ...rapide.points, ...lent.points],
      320,
      120,
    )!;

    const y = ordonnees(graphe.chemin);
    const auQuart = y[Math.floor(y.length * 0.25)];
    const auxTroisQuarts = y[Math.floor(y.length * 0.75)];

    expect(auQuart).toBeLessThan(auxTroisQuarts);
    expect(graphe.allureRapideKmh).toBeGreaterThan(graphe.allureLenteKmh);
    expect(graphe.allureRapideKmh).toBeGreaterThan(15);
    expect(graphe.allureLenteKmh).toBeLessThan(8);
  });

  it('borne l’échelle sur les centiles plutôt que sur un point aberrant', () => {
    const regulier = segment(0, 2000, 0, 40);
    const graphe = construireGrapheAllure([point(0, 0), ...regulier.points], 320, 120)!;

    expect(graphe.allureRapideKmh).toBeLessThan(12);
    expect(graphe.allureLenteKmh).toBeGreaterThan(6);
  });

  // WHY: une pause de dix minutes au feu rouge ne doit ni allonger le parcours, ni écraser la
  // courbe vers le bas — c'est le même contrat que la mesure et le serveur.
  it('ignore le temps passé en coupure', () => {
    const avant = segment(0, 1000, 0, 30);
    const reprise = point(1000, avant.finS + 600, true);
    const apres = segment(1000, 2000, avant.finS + 600, 30);
    const graphe = construireGrapheAllure(
      [point(0, 0), ...avant.points, reprise, ...apres.points],
      320,
      120,
    )!;

    expect(graphe.distanceM).toBeCloseTo(2000, 0);
    expect(graphe.allureLenteKmh).toBeGreaterThan(8);
  });

  it('ferme l’aire sous la courbe', () => {
    const { points } = segment(0, 1000, 0, 30);
    const graphe = construireGrapheAllure([point(0, 0), ...points], 320, 120)!;

    expect(graphe.aire.startsWith(graphe.chemin)).toBe(true);
    expect(graphe.aire.endsWith('Z')).toBe(true);
  });
});
