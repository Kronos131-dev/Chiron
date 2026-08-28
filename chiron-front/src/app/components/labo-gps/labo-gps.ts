import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { HeaderComponent } from '../shared/header/header';
import {
  NomStrategie,
  Survie,
  strategieDisponible,
  tenirEnVie,
} from '../../util/survie-arriere-plan';

type TypeEvenement =
  | 'debut'
  | 'gps'
  | 'gps-erreur'
  | 'horloge-gelee'
  | 'cache'
  | 'visible'
  | 'voix-ok'
  | 'voix-erreur'
  | 'etat'
  | 'fin';

interface Evenement {
  t: number;
  type: TypeEvenement;
  detail: string;
}

interface Releve {
  demarreA: number;
  strategies: NomStrategie[];
  evenements: Evenement[];
}

const CLE_STOCKAGE = 'chiron.labo-gps';
const TICK_MS = 1000;
const SEUIL_GEL_MS = 3000;
const PERIODE_VOIX_MS = 120000;
const PERIODE_ETAT_MS = 60000;
const GAP_GPS_SAIN_S = 15;
const GAP_GPS_BRIDE_S = 60;
const PRECISION_DEGRES = 5;

@Component({
  selector: 'app-labo-gps',
  standalone: true,
  imports: [CommonModule, HeaderComponent],
  templateUrl: './labo-gps.html',
  styleUrl: './labo-gps.css',
})
export class LaboGps implements OnInit, OnDestroy {
  private router = inject(Router);

  readonly enCours = signal(false);
  readonly releve = signal<Releve | null>(null);

  readonly strategies = signal<Record<NomStrategie, boolean>>({
    audioElement: true,
    webAudio: false,
    webLock: true,
    wakeLock: false,
  });

  private survie: Survie | null = null;
  private veilleurGps: number | null = null;
  private ticker: number | null = null;
  private dernierTick = 0;
  private dernierGps = 0;
  private prochaineVoix = 0;
  private prochainEtat = 0;
  private surVisibilite = () => this.noterVisibilite();

  readonly nomsStrategies: NomStrategie[] = ['audioElement', 'webAudio', 'webLock', 'wakeLock'];

  readonly libelles: Record<NomStrategie, string> = {
    audioElement: 'Boucle audio inaudible (risque de couper ta musique)',
    webAudio: 'Oscillateur Web Audio (plus discret, moins fiable)',
    webLock: 'Web Lock (aucun son, aucun conflit)',
    wakeLock: 'Wake Lock écran (témoin : écran allumé)',
  };

  ngOnInit(): void {
    this.releve.set(this.lireReleve());
    document.addEventListener('visibilitychange', this.surVisibilite);
  }

  ngOnDestroy(): void {
    document.removeEventListener('visibilitychange', this.surVisibilite);
    this.arreterMesures();
  }

  disponible(nom: NomStrategie): boolean {
    return strategieDisponible(nom);
  }

  basculer(nom: NomStrategie): void {
    if (this.enCours()) return;
    this.strategies.update((s) => ({ ...s, [nom]: !s[nom] }));
  }

  demarrer(): void {
    if (this.enCours()) return;
    if (!navigator.geolocation) {
      this.releve.set({
        demarreA: Date.now(),
        strategies: [],
        evenements: [{ t: 0, type: 'gps-erreur', detail: 'navigator.geolocation absent' }],
      });
      return;
    }

    const choisies = this.nomsStrategies.filter((n) => this.strategies()[n] && this.disponible(n));
    const depart = Date.now();
    this.releve.set({ demarreA: depart, strategies: choisies, evenements: [] });
    this.enCours.set(true);
    this.dernierTick = depart;
    this.dernierGps = depart;
    this.prochaineVoix = depart + PERIODE_VOIX_MS;
    this.prochainEtat = depart + PERIODE_ETAT_MS;

    this.survie = tenirEnVie(choisies);
    this.noter('debut', choisies.length ? choisies.join(' + ') : 'aucune stratégie');

    this.veilleurGps = navigator.geolocation.watchPosition(
      (position) => this.noterPosition(position),
      (erreur) => this.noter('gps-erreur', `code ${erreur.code} — ${erreur.message}`),
      { enableHighAccuracy: true, maximumAge: 0, timeout: 30000 },
    );
    this.ticker = window.setInterval(() => this.tick(), TICK_MS);
  }

  arreter(): void {
    if (!this.enCours()) return;
    this.noter('fin', 'arrêt manuel');
    this.arreterMesures();
    this.enCours.set(false);
  }

  effacer(): void {
    if (this.enCours()) return;
    try {
      localStorage.removeItem(CLE_STOCKAGE);
    } catch {}
    this.releve.set(null);
  }

  async copier(): Promise<void> {
    const releve = this.releve();
    if (!releve) return;
    try {
      await navigator.clipboard.writeText(JSON.stringify(releve, null, 2));
    } catch {}
  }

  quitter(): void {
    this.router.navigate(['/chat']);
  }

  private arreterMesures(): void {
    if (this.veilleurGps !== null) navigator.geolocation.clearWatch(this.veilleurGps);
    if (this.ticker !== null) clearInterval(this.ticker);
    this.veilleurGps = null;
    this.ticker = null;
    this.survie?.relacher();
    this.survie = null;
  }

  private tick(): void {
    const maintenant = Date.now();
    const ecart = maintenant - this.dernierTick;
    if (ecart > SEUIL_GEL_MS) {
      this.noter('horloge-gelee', `${Math.round(ecart / 1000)} s sans tick`);
    }
    this.dernierTick = maintenant;

    if (maintenant >= this.prochaineVoix) {
      this.prochaineVoix = maintenant + PERIODE_VOIX_MS;
      this.testerVoix();
    }
    if (maintenant >= this.prochainEtat) {
      this.prochainEtat = maintenant + PERIODE_ETAT_MS;
      this.noter('etat', `visibilité ${document.visibilityState}`);
    }
  }

  private noterPosition(position: GeolocationPosition): void {
    const maintenant = Date.now();
    const ecart = Math.round((maintenant - this.dernierGps) / 1000);
    this.dernierGps = maintenant;
    const lat = position.coords.latitude.toFixed(PRECISION_DEGRES);
    const lon = position.coords.longitude.toFixed(PRECISION_DEGRES);
    const precision = Math.round(position.coords.accuracy);
    this.noter('gps', `+${ecart}s · ±${precision}m · ${lat},${lon}`);
  }

  private noterVisibilite(): void {
    if (!this.enCours()) return;
    this.noter(
      document.visibilityState === 'visible' ? 'visible' : 'cache',
      document.visibilityState,
    );
  }

  private testerVoix(): void {
    const synthese = window.speechSynthesis;
    if (!synthese) {
      this.noter('voix-erreur', 'speechSynthesis absent');
      return;
    }
    const message = new SpeechSynthesisUtterance('test');
    message.lang = 'fr-FR';
    message.volume = 0.2;
    message.onend = () => this.noter('voix-ok', 'prononcé');
    message.onerror = () => this.noter('voix-erreur', 'onerror');
    synthese.speak(message);
  }

  private noter(type: TypeEvenement, detail: string): void {
    const releve = this.releve();
    if (!releve) return;
    const evenement: Evenement = { t: Date.now() - releve.demarreA, type, detail };
    const suivant: Releve = { ...releve, evenements: [...releve.evenements, evenement] };
    this.releve.set(suivant);
    this.ecrireReleve(suivant);
  }

  private ecrireReleve(releve: Releve): void {
    try {
      localStorage.setItem(CLE_STOCKAGE, JSON.stringify(releve));
    } catch {}
  }

  private lireReleve(): Releve | null {
    try {
      const brut = localStorage.getItem(CLE_STOCKAGE);
      return brut ? (JSON.parse(brut) as Releve) : null;
    } catch {
      return null;
    }
  }

  readonly evenementsRecents = computed(() => {
    const evenements = this.releve()?.evenements ?? [];
    return evenements.slice(-40).reverse();
  });

  readonly dureeS = computed(() => {
    const evenements = this.releve()?.evenements ?? [];
    return evenements.length ? Math.round(evenements[evenements.length - 1].t / 1000) : 0;
  });

  private readonly ecartsGpsS = computed(() => {
    const evenements = this.releve()?.evenements ?? [];
    const instants = evenements.filter((e) => e.type === 'gps').map((e) => e.t);
    const ecarts: number[] = [];
    for (let i = 1; i < instants.length; i++) ecarts.push((instants[i] - instants[i - 1]) / 1000);
    return ecarts;
  });

  readonly nbPoints = computed(
    () => (this.releve()?.evenements ?? []).filter((e) => e.type === 'gps').length,
  );

  readonly ecartGpsMaxS = computed(() => {
    const ecarts = this.ecartsGpsS();
    return ecarts.length ? Math.round(Math.max(...ecarts)) : 0;
  });

  readonly ecartGpsMedianS = computed(() => {
    const ecarts = [...this.ecartsGpsS()].sort((a, b) => a - b);
    if (!ecarts.length) return 0;
    return Math.round(ecarts[Math.floor(ecarts.length / 2)] * 10) / 10;
  });

  readonly gelMaxS = computed(() => {
    const gels = (this.releve()?.evenements ?? [])
      .filter((e) => e.type === 'horloge-gelee')
      .map((e) => parseInt(e.detail, 10))
      .filter((n) => !isNaN(n));
    return gels.length ? Math.max(...gels) : 0;
  });

  readonly tempsCacheS = computed(() => {
    const evenements = this.releve()?.evenements ?? [];
    let total = 0;
    let cacheDepuis: number | null = null;
    for (const e of evenements) {
      if (e.type === 'cache') cacheDepuis = e.t;
      if (e.type === 'visible' && cacheDepuis !== null) {
        total += e.t - cacheDepuis;
        cacheDepuis = null;
      }
    }
    const dernier = evenements.length ? evenements[evenements.length - 1].t : 0;
    if (cacheDepuis !== null) total += dernier - cacheDepuis;
    return Math.round(total / 1000);
  });

  readonly voixOk = computed(
    () => (this.releve()?.evenements ?? []).filter((e) => e.type === 'voix-ok').length,
  );

  readonly voixEchouees = computed(
    () => (this.releve()?.evenements ?? []).filter((e) => e.type === 'voix-erreur').length,
  );

  readonly verdict = computed(() => {
    if (!this.releve()) return '';
    if (this.tempsCacheS() < 60)
      return 'Écran jamais éteint assez longtemps — le test ne conclut rien.';
    if (this.nbPoints() === 0) return 'Aucune position reçue : le GPS ne démarre pas.';
    if (this.ecartGpsMaxS() <= GAP_GPS_SAIN_S)
      return 'Le GPS a tenu écran éteint. La page Course est faisable telle quelle.';
    if (this.ecartGpsMaxS() <= GAP_GPS_BRIDE_S)
      return 'Le GPS est bridé mais vivant : il faudra lisser l’allure sur une fenêtre plus large.';
    return 'Le GPS a été coupé écran éteint : replier sur le Wake Lock, écran allumé.';
  });
}
