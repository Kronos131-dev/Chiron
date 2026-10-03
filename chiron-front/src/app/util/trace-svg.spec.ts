import { describe, expect, it } from 'vitest';
import { CoursePointDto } from '../service/chiron-api';
import { projeterPointsDans } from './trace-svg';

function point(lat: number, lon: number, t: number, coupure = false): CoursePointDto {
  return { lat, lon, t, alt: null, coupure };
}

const BOUCLE = [
  point(48.85, 2.35, 0),
  point(48.851, 2.352, 10_000),
  point(48.852, 2.35, 20_000),
  point(48.851, 2.348, 30_000),
  point(48.85, 2.35, 40_000),
];

describe('projeterPointsDans', () => {
  it('garde tous les points dans le rectangle, marge comprise', () => {
    const trace = projeterPointsDans(BOUCLE, 800, 400, 40);
    expect(trace.segments).toHaveLength(4);
    for (const segment of trace.segments) {
      for (const p of [segment.de, segment.vers]) {
        expect(p.x).toBeGreaterThanOrEqual(40 - 1e-6);
        expect(p.x).toBeLessThanOrEqual(760 + 1e-6);
        expect(p.y).toBeGreaterThanOrEqual(40 - 1e-6);
        expect(p.y).toBeLessThanOrEqual(360 + 1e-6);
      }
    }
  });

  it('place le nord en haut', () => {
    const trace = projeterPointsDans(BOUCLE, 800, 400, 40);
    expect(trace.segments[0].vers.y).toBeLessThan(trace.segments[0].de.y);
  });

  it('retourne départ et arrivée', () => {
    const trace = projeterPointsDans(BOUCLE, 800, 400, 40);
    expect(trace.depart).not.toBeNull();
    expect(trace.arrivee).not.toBeNull();
  });

  it('saute le segment qui suit une pause', () => {
    const avecPause = [...BOUCLE.slice(0, 3), point(48.8515, 2.349, 90_000, true), BOUCLE[4]];
    expect(projeterPointsDans(avecPause, 800, 400, 40).segments).toHaveLength(3);
  });

  it('retourne une trace vide sous deux points', () => {
    const trace = projeterPointsDans([BOUCLE[0]], 800, 400, 40);
    expect(trace.segments).toEqual([]);
    expect(trace.depart).toBeNull();
  });
});
