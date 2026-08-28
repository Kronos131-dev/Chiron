import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { CoursePointDto } from './chiron-api';
import { CLE_STOCKAGE_COURSE, CourseTracker, allureKmh, mesurer } from './course-tracker';

const LAT_DEPART = 48.8566;
const LON_DEPART = 2.3522;
const T_DEPART = 1_700_000_000_000;
const DEGRE_LATITUDE_EN_METRES = (2 * Math.PI * 6371008.8) / 360;

function point(metres: number, secondes: number, extras: Partial<CoursePointDto> = {}) {
  return {
    lat: LAT_DEPART + metres / DEGRE_LATITUDE_EN_METRES,
    lon: LON_DEPART,
    t: T_DEPART + secondes * 1000,
    alt: null,
    ...extras,
  } as CoursePointDto;
}

function ligneDroite(metresTotal: number, nbPoints: number, dureeS: number): CoursePointDto[] {
  return Array.from({ length: nbPoints }, (_, i) => {
    const fraction = i / (nbPoints - 1);
    return point(metresTotal * fraction, Math.round(dureeS * fraction));
  });
}

describe('mesurer', () => {
  it('retrouve la distance d’une ligne droite d’un kilomètre', () => {
    expect(mesurer(ligneDroite(1000, 11, 300)).distanceM).toBeCloseTo(1000, -1);
  });

  it('produit un split par kilomètre franchi', () => {
    const mesures = mesurer(ligneDroite(2500, 251, 750));
    expect(mesures.splits.map((s) => s.kilometre)).toEqual([1, 2]);
    expect(mesures.splits[0].dureeS).toBeCloseTo(300, -1);
  });

  it('ne produit aucun split sous le kilomètre', () => {
    expect(mesurer(ligneDroite(600, 21, 200)).splits).toHaveLength(0);
  });

  it('ignore la distance parcourue pendant une pause', () => {
    const points = [
      point(0, 0),
      point(500, 150),
      point(2500, 900, { coupure: true }),
      point(3000, 1050),
    ];
    expect(mesurer(points).distanceM).toBeCloseTo(1000, -1);
  });

  it('n’ajoute pas la durée d’une pause au temps du kilomètre', () => {
    const points = [
      point(0, 0),
      point(500, 150),
      point(500, 900, { coupure: true }),
      point(1000, 1050),
      point(1500, 1200),
    ];
    const mesures = mesurer(points);
    expect(mesures.splits).toHaveLength(1);
    expect(mesures.splits[0].dureeS).toBe(300);
  });

  it('rend des mesures vides sous deux points', () => {
    expect(mesurer([]).distanceM).toBe(0);
    expect(mesurer([point(0, 0)]).splits).toHaveLength(0);
  });
});

describe('allureKmh', () => {
  it('donne douze km/h pour un kilomètre en cinq minutes', () => {
    expect(allureKmh(1000, 300)).toBeCloseTo(12, 5);
  });

  it('donne zéro sur une durée nulle', () => {
    expect(allureKmh(1000, 0)).toBe(0);
  });
});

describe('CourseTracker', () => {
  let tracker: CourseTracker;
  let emettrePosition: (position: any) => void;
  let clearWatch: ReturnType<typeof vi.fn>;

  function position(
    metres: number,
    secondes: number,
    precision = 5,
    altitude: number | null = null,
  ) {
    return {
      coords: {
        latitude: LAT_DEPART + metres / DEGRE_LATITUDE_EN_METRES,
        longitude: LON_DEPART,
        accuracy: precision,
        altitude,
      },
      timestamp: T_DEPART + secondes * 1000,
    };
  }

  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(T_DEPART);
    localStorage.clear();
    clearWatch = vi.fn();
    emettrePosition = () => {};
    vi.stubGlobal('navigator', {
      ...navigator,
      geolocation: {
        watchPosition: (succes: any) => {
          emettrePosition = succes;
          return 1;
        },
        clearWatch,
      },
    });
    tracker = new CourseTracker();
    tracker.attacher('7', 'exo-1');
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  it('rejette une position dont la précision dépasse vingt-cinq mètres', () => {
    tracker.demarrer();
    emettrePosition(position(0, 0, 40));
    expect(tracker.nbPoints()).toBe(0);
    expect(tracker.precisionM()).toBe(40);
  });

  it('ignore un déplacement de moins de trois mètres', () => {
    tracker.demarrer();
    emettrePosition(position(0, 0));
    emettrePosition(position(1.5, 5));
    expect(tracker.nbPoints()).toBe(1);
  });

  it('accumule la distance des déplacements réels', () => {
    tracker.demarrer();
    emettrePosition(position(0, 0));
    emettrePosition(position(100, 30));
    emettrePosition(position(200, 60));
    expect(tracker.distanceM()).toBeCloseTo(200, -1);
  });

  it('n’enregistre plus rien en pause', () => {
    tracker.demarrer();
    emettrePosition(position(0, 0));
    tracker.basculerPause();
    emettrePosition(position(500, 300));
    expect(tracker.nbPoints()).toBe(1);
    expect(clearWatch).toHaveBeenCalled();
  });

  // WHY: c'est le cas qui fausse une sortie — une pause déjeuner ou un trajet en voiture entre
  // deux boucles doit laisser la distance intacte.
  it('marque d’une coupure le premier point d’après la reprise et n’en compte pas le segment', () => {
    tracker.demarrer();
    emettrePosition(position(0, 0));
    emettrePosition(position(200, 60));
    tracker.basculerPause();
    tracker.basculerPause();
    emettrePosition(position(5000, 1800));
    emettrePosition(position(5100, 1830));

    expect(tracker.points()[2].coupure).toBe(true);
    expect(tracker.distanceM()).toBeCloseTo(300, -1);
  });

  it('ne compte pas le temps passé en pause dans la durée', () => {
    tracker.demarrer();
    vi.setSystemTime(T_DEPART + 60_000);
    tracker.basculerPause();
    vi.setSystemTime(T_DEPART + 600_000);
    tracker.basculerPause();
    vi.setSystemTime(T_DEPART + 660_000);
    tracker.rafraichir(T_DEPART + 660_000);

    expect(tracker.dureeS()).toBe(120);
  });

  it('signale la perte du signal après vingt secondes sans position', () => {
    tracker.demarrer();
    emettrePosition(position(0, 0));
    tracker.rafraichir(T_DEPART + 25_000);
    expect(tracker.signalPerdu()).toBe(true);
  });

  describe('reprise après un rechargement', () => {
    it('restaure les points et la durée d’une course en cours', () => {
      tracker.demarrer();
      emettrePosition(position(0, 0));
      emettrePosition(position(400, 120));
      vi.setSystemTime(T_DEPART + 120_000);
      tracker.ecrireSnapshot({ cibleMinParKm: 5.5, kmAnnonces: 0 });

      const repris = new CourseTracker();
      repris.attacher('7', 'exo-1');
      expect(repris.restaurer()).toBe(true);
      expect(repris.nbPoints()).toBe(2);
      expect(repris.etat()).toBe('enCours');
      expect(repris.distanceM()).toBeCloseTo(400, -1);
      expect(repris.lireSnapshot()?.cibleMinParKm).toBe(5.5);
    });

    it('refuse un instantané qui appartient à une autre séance', () => {
      tracker.demarrer();
      emettrePosition(position(0, 0));

      const autre = new CourseTracker();
      autre.attacher('9', 'exo-2');
      expect(autre.restaurer()).toBe(false);
      expect(autre.nbPoints()).toBe(0);
    });

    it('ouvre une coupure sur le premier point reçu après la restauration', () => {
      tracker.demarrer();
      emettrePosition(position(0, 0));
      emettrePosition(position(300, 90));

      const repris = new CourseTracker();
      repris.attacher('7', 'exo-1');
      repris.restaurer();
      emettrePosition(position(9000, 3600));

      expect(repris.points()[2].coupure).toBe(true);
      expect(repris.distanceM()).toBeCloseTo(300, -1);
    });

    it('purge l’instantané', () => {
      tracker.demarrer();
      tracker.purgerSnapshot();
      expect(localStorage.getItem(CLE_STOCKAGE_COURSE)).toBeNull();
    });
  });
});
