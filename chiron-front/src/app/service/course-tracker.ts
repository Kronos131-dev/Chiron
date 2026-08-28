import { computed, signal } from '@angular/core';
import { CoursePointDto, CourseSplitDto } from './chiron-api';

export type EtatCourse = 'pret' | 'enCours' | 'enPause' | 'termine';

export interface MesuresCourse {
  distanceM: number;
  splits: CourseSplitDto[];
  parcours: JalonParcours[];
}

interface JalonParcours {
  distanceM: number;
  tCourseMs: number;
}

export interface SnapshotCourse {
  routineId: string;
  exoId: string;
  points: CoursePointDto[];
  msAccumules: number;
  repriseA: number | null;
  cibleMinParKm: number | null;
  kmAnnonces: number;
}

const RAYON_TERRE_M = 6371008.8;
const KM_EN_METRES = 1000;
const MS_PAR_SECONDE = 1000;
const SECONDES_PAR_HEURE = 3600;

const PRECISION_MAX_M = 25;
const DEPLACEMENT_MIN_M = 3;
const FENETRE_ALLURE_MS = 30000;
const SILENCE_GPS_MS = 20000;
const DECIMALES_DEGRES = 5;

export const CLE_STOCKAGE_COURSE = 'chiron.course';

export function distanceHaversineM(a: CoursePointDto, b: CoursePointDto): number {
  const lat1 = (a.lat * Math.PI) / 180;
  const lat2 = (b.lat * Math.PI) / 180;
  const deltaLat = lat2 - lat1;
  const deltaLon = ((b.lon - a.lon) * Math.PI) / 180;
  const h =
    Math.sin(deltaLat / 2) ** 2 + Math.cos(lat1) * Math.cos(lat2) * Math.sin(deltaLon / 2) ** 2;
  return 2 * RAYON_TERRE_M * Math.asin(Math.min(1, Math.sqrt(h)));
}

export function allureKmh(distanceM: number, dureeS: number): number {
  if (dureeS <= 0 || distanceM <= 0) return 0;
  return distanceM / KM_EN_METRES / (dureeS / SECONDES_PAR_HEURE);
}

// WHY: cet algorithme est le jumeau de CourseGeometrieServiceImpl.mesurer côté serveur. Les
// deux doivent coïncider : l'écran affiche celui-ci pendant l'effort, le journal conserve
// celui-là, et un athlète qui voit 10,2 km à l'arrivée puis 9,7 km dans son journal cesse de
// faire confiance aux deux.
export function mesurer(points: CoursePointDto[]): MesuresCourse {
  if (points.length < 2) {
    const jalon = points.length === 1 ? [{ distanceM: 0, tCourseMs: 0 }] : [];
    return { distanceM: 0, splits: [], parcours: jalon };
  }

  const splits: CourseSplitDto[] = [];
  const parcours: JalonParcours[] = [{ distanceM: 0, tCourseMs: 0 }];
  const depart = points[0].t;
  let cumulM = 0;
  let pauseCumuleeMs = 0;
  let debutKmMs = 0;
  let prochainKm = 1;

  for (let i = 1; i < points.length; i++) {
    const precedent = points[i - 1];
    const courant = points[i];

    if (courant.coupure) {
      pauseCumuleeMs += courant.t - precedent.t;
      parcours.push({ distanceM: cumulM, tCourseMs: courant.t - depart - pauseCumuleeMs });
      continue;
    }

    const segmentM = distanceHaversineM(precedent, courant);
    const debutSegmentM = cumulM;
    cumulM += segmentM;

    const debutSegmentMs = precedent.t - depart - pauseCumuleeMs;
    const finSegmentMs = courant.t - depart - pauseCumuleeMs;
    parcours.push({ distanceM: cumulM, tCourseMs: finSegmentMs });

    while (segmentM > 0 && cumulM >= prochainKm * KM_EN_METRES) {
      const fraction = (prochainKm * KM_EN_METRES - debutSegmentM) / segmentM;
      const instantMs = debutSegmentMs + Math.round(fraction * (finSegmentMs - debutSegmentMs));
      const splitS = Math.round((instantMs - debutKmMs) / MS_PAR_SECONDE);
      splits.push({
        kilometre: prochainKm,
        dureeS: splitS,
        allureKmh: allureKmh(KM_EN_METRES, splitS),
      });
      debutKmMs = instantMs;
      prochainKm++;
    }
  }

  return { distanceM: cumulM, splits, parcours };
}

export class CourseTracker {
  readonly etat = signal<EtatCourse>('pret');
  readonly points = signal<CoursePointDto[]>([]);
  readonly precisionM = signal<number | null>(null);
  readonly erreurGps = signal<string | null>(null);

  private readonly maintenant = signal(Date.now());
  private readonly derniereReception = signal(0);
  private readonly mesures = computed(() => mesurer(this.points()));

  private veilleur: number | null = null;
  private msAccumules = 0;
  private repriseA: number | null = null;
  private ouvrirUneCoupure = false;
  private routineId = '';
  private exoId = '';
  private cibleMinParKm: number | null = null;
  private kmAnnonces = 0;

  readonly distanceM = computed(() => this.mesures().distanceM);
  readonly splits = computed(() => this.mesures().splits);

  readonly dureeMs = computed(() => {
    const debut = this.repriseA;
    return this.msAccumules + (debut === null ? 0 : Math.max(0, this.maintenant() - debut));
  });

  readonly dureeS = computed(() => Math.floor(this.dureeMs() / MS_PAR_SECONDE));

  readonly allureMoyenneKmh = computed(() => allureKmh(this.distanceM(), this.dureeS()));

  // WHY: l'allure instantanée d'un point au suivant saute de 8 à 16 km/h sous les arbres. Une
  // fenêtre glissante de trente secondes est ce qui rend le chiffre annonçable sans mentir.
  readonly allureCouranteKmh = computed(() => {
    if (this.etat() !== 'enCours') return 0;
    const parcours = this.mesures().parcours;
    if (parcours.length < 2) return 0;

    const fin = parcours[parcours.length - 1];
    let debut = parcours[0];
    for (let i = parcours.length - 1; i >= 0; i--) {
      debut = parcours[i];
      if (fin.tCourseMs - parcours[i].tCourseMs >= FENETRE_ALLURE_MS) break;
    }
    const dureeS = (fin.tCourseMs - debut.tCourseMs) / MS_PAR_SECONDE;
    return allureKmh(fin.distanceM - debut.distanceM, Math.round(dureeS));
  });

  readonly signalPerdu = computed(() => {
    if (this.etat() !== 'enCours') return false;
    const derniere = this.derniereReception();
    return derniere > 0 && this.maintenant() - derniere > SILENCE_GPS_MS;
  });

  readonly nbPoints = computed(() => this.points().length);

  attacher(routineId: string, exoId: string): void {
    this.routineId = routineId;
    this.exoId = exoId;
  }

  rafraichir(instant: number): void {
    this.maintenant.set(instant);
  }

  demarrer(): void {
    if (this.etat() !== 'pret') return;
    const depart = Date.now();
    this.msAccumules = 0;
    this.repriseA = depart;
    this.maintenant.set(depart);
    this.derniereReception.set(depart);
    this.etat.set('enCours');
    this.ecouterGps();
    this.ecrireSnapshot();
  }

  basculerPause(): void {
    if (this.etat() === 'enCours') this.mettreEnPause();
    else if (this.etat() === 'enPause') this.reprendre();
  }

  private mettreEnPause(): void {
    const debut = this.repriseA;
    if (debut !== null) this.msAccumules += Math.max(0, Date.now() - debut);
    this.repriseA = null;
    this.couperGps();
    this.etat.set('enPause');
    this.ecrireSnapshot();
  }

  private reprendre(): void {
    const reprise = Date.now();
    this.repriseA = reprise;
    this.maintenant.set(reprise);
    this.derniereReception.set(reprise);
    // WHY: le premier point capté après une reprise est à une distance arbitraire du dernier
    // point d'avant la pause. Le marquer d'une coupure est ce qui empêche un trajet en voiture
    // ou une pause déjeuner de s'ajouter à la sortie, ici comme côté serveur.
    this.ouvrirUneCoupure = true;
    this.etat.set('enCours');
    this.ecouterGps();
    this.ecrireSnapshot();
  }

  arreter(): void {
    if (this.etat() === 'termine') return;
    const debut = this.repriseA;
    if (debut !== null) this.msAccumules += Math.max(0, Date.now() - debut);
    this.repriseA = null;
    this.couperGps();
    this.etat.set('termine');
    this.ecrireSnapshot();
  }

  liberer(): void {
    this.couperGps();
  }

  private ecouterGps(): void {
    if (this.veilleur !== null || !navigator.geolocation) return;
    this.veilleur = navigator.geolocation.watchPosition(
      (position) => this.accepterPosition(position),
      (erreur) => this.erreurGps.set(erreur.message || String(erreur.code)),
      { enableHighAccuracy: true, maximumAge: 0, timeout: 30000 },
    );
  }

  private couperGps(): void {
    if (this.veilleur === null) return;
    navigator.geolocation.clearWatch(this.veilleur);
    this.veilleur = null;
  }

  // WHY: sans ces deux filtres la dérive GPS à l'arrêt ajoute des centaines de mètres à une
  // sortie — un feu rouge de deux minutes suffit à inventer 200 m.
  private accepterPosition(position: GeolocationPosition): void {
    if (this.etat() !== 'enCours') return;
    this.erreurGps.set(null);
    this.derniereReception.set(Date.now());
    this.precisionM.set(Math.round(position.coords.accuracy));

    if (position.coords.accuracy > PRECISION_MAX_M) return;

    const point: CoursePointDto = {
      lat: arrondirDegres(position.coords.latitude),
      lon: arrondirDegres(position.coords.longitude),
      t: position.timestamp || Date.now(),
      alt: position.coords.altitude ?? null,
    };

    const existants = this.points();
    const dernier = existants[existants.length - 1];

    if (this.ouvrirUneCoupure) {
      this.ouvrirUneCoupure = false;
      if (dernier) point.coupure = true;
    } else if (dernier && distanceHaversineM(dernier, point) < DEPLACEMENT_MIN_M) {
      return;
    }

    this.points.set([...existants, point]);
    this.ecrireSnapshot();
  }

  restaurer(): boolean {
    let brut: string | null = null;
    try {
      brut = localStorage.getItem(CLE_STOCKAGE_COURSE);
    } catch {
      return false;
    }
    if (!brut) return false;

    try {
      const data = JSON.parse(brut) as SnapshotCourse;
      if (data.routineId !== this.routineId || data.exoId !== this.exoId) return false;
      if (!Array.isArray(data.points)) return false;

      this.points.set(data.points);
      this.msAccumules = data.msAccumules ?? 0;
      this.repriseA = data.repriseA ?? null;
      this.cibleMinParKm = data.cibleMinParKm ?? null;
      this.kmAnnonces = data.kmAnnonces ?? 0;
      this.maintenant.set(Date.now());
      this.derniereReception.set(Date.now());

      if (this.repriseA !== null) {
        this.etat.set('enCours');
        // WHY: la page a pu être rechargée à côté de la course. La reprise démarre un nouveau
        // segment, dont le premier point ne doit pas être relié au dernier point d'avant.
        this.ouvrirUneCoupure = true;
        this.ecouterGps();
      } else if (this.points().length > 0 || this.msAccumules > 0) {
        this.etat.set('enPause');
      }
      return true;
    } catch {
      return false;
    }
  }

  lireSnapshot(): SnapshotCourse | null {
    try {
      const brut = localStorage.getItem(CLE_STOCKAGE_COURSE);
      return brut ? (JSON.parse(brut) as SnapshotCourse) : null;
    } catch {
      return null;
    }
  }

  // WHY: appelée à chaque position reçue, donc environ une fois par seconde pendant une heure.
  // La cible et les kilomètres annoncés sont tenus en mémoire plutôt que relus du stockage :
  // relire imposerait deux analyses JSON de toute la trace à chaque point capté.
  ecrireSnapshot(extras?: Partial<SnapshotCourse>): void {
    if (extras?.cibleMinParKm !== undefined) this.cibleMinParKm = extras.cibleMinParKm;
    if (extras?.kmAnnonces !== undefined) this.kmAnnonces = extras.kmAnnonces;

    const data: SnapshotCourse = {
      routineId: this.routineId,
      exoId: this.exoId,
      points: this.points(),
      msAccumules: this.msAccumules,
      repriseA: this.repriseA,
      cibleMinParKm: this.cibleMinParKm,
      kmAnnonces: this.kmAnnonces,
    };
    try {
      localStorage.setItem(CLE_STOCKAGE_COURSE, JSON.stringify(data));
    } catch {}
  }

  purgerSnapshot(): void {
    try {
      localStorage.removeItem(CLE_STOCKAGE_COURSE);
    } catch {}
  }
}

function arrondirDegres(valeur: number): number {
  const facteur = 10 ** DECIMALES_DEGRES;
  return Math.round(valeur * facteur) / facteur;
}
