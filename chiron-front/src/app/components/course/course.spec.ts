import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { signal } from '@angular/core';
import { of, throwError } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { Course } from './course';
import { ActiveSessionService } from '../../service/active-session.service';
import { ChironApi } from '../../service/chiron-api';
import { AuthService } from '../../service/auth.service';
import { ExerciceForm } from '../../shared/exercise-forms';
import { CLE_STOCKAGE_COURSE } from '../../service/course-tracker';
import { RuntimeWeb } from '../../service/course-runtime-web';
import { fr } from '../../i18n/fr';

const LAT_DEPART = 48.8566;
const LON_DEPART = 2.3522;
const T_DEPART = 1_700_000_000_000;
const DEGRE_LATITUDE_EN_METRES = (2 * Math.PI * 6371008.8) / 360;

describe('Course', () => {
  let component: Course;
  let fixture: ComponentFixture<Course>;
  let router: { navigate: ReturnType<typeof vi.fn> };
  let activeSession: {
    exercices: ReturnType<typeof signal<ExerciceForm[]>>;
    snapshot: ReturnType<typeof vi.fn>;
  };
  let chironApi: {
    getProfile: ReturnType<typeof vi.fn>;
    enregistrerTraceCourse: ReturnType<typeof vi.fn>;
  };
  let emettrePosition: (position: any) => void;
  let paroles: string[];
  let volumes: number[];

  function exoCourse(id: string): ExerciceForm {
    return {
      id,
      nom: 'Course en extérieur',
      definitionId: 42,
      cardioType: 'COURSE_EXTERIEUR',
      series: [
        {
          id: 's1',
          poids: null,
          reps: null,
          degressifs: [],
          dureeMin: null,
          distanceM: null,
          allureKmh: null,
          courseTraceId: null,
        },
      ],
    };
  }

  function web(): RuntimeWeb {
    return component.runtime as RuntimeWeb;
  }

  function position(metres: number, secondes: number, precision = 5) {
    return {
      coords: {
        latitude: LAT_DEPART + metres / DEGRE_LATITUDE_EN_METRES,
        longitude: LON_DEPART,
        accuracy: precision,
        altitude: null,
      },
      timestamp: T_DEPART + secondes * 1000,
    };
  }

  async function boot(exercices: ExerciceForm[], exoId: string) {
    activeSession = { exercices: signal<ExerciceForm[]>(exercices), snapshot: vi.fn() };
    router = { navigate: vi.fn() };
    chironApi = {
      getProfile: vi.fn().mockReturnValue({ subscribe: () => {} }),
      enregistrerTraceCourse: vi.fn().mockReturnValue(
        of({
          id: 77,
          distanceM: 1000,
          dureeS: 300,
          allureMoyenneKmh: 12,
          denivelePositifM: 4,
          splits: [{ kilometre: 1, dureeS: 300, allureKmh: 12 }],
          points: [],
        }),
      ),
    };

    await TestBed.configureTestingModule({
      imports: [Course],
      providers: [
        { provide: Router, useValue: router },
        { provide: ActiveSessionService, useValue: activeSession },
        { provide: ChironApi, useValue: chironApi },
        { provide: AuthService, useValue: { getUsername: vi.fn().mockReturnValue('alice') } },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: convertToParamMap({ id: '7', exoId }) } },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(Course);
    component = fixture.componentInstance;
    fixture.detectChanges();
    for (let i = 0; i < 5; i++) await Promise.resolve();
    fixture.detectChanges();
  }

  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(T_DEPART);
    localStorage.clear();
    localStorage.setItem('chiron_lang', 'fr');
    paroles = [];
    volumes = [];
    emettrePosition = () => {};

    Object.defineProperty(navigator, 'geolocation', {
      configurable: true,
      value: {
        watchPosition: (succes: any) => {
          emettrePosition = succes;
          return 1;
        },
        clearWatch: vi.fn(),
      },
    });
    vi.stubGlobal('speechSynthesis', {
      speak: (message: any) => {
        paroles.push(message.text);
        volumes.push(message.volume);
      },
      cancel: vi.fn(),
      paused: false,
    });
    vi.stubGlobal(
      'SpeechSynthesisUtterance',
      class {
        text: string;
        lang = '';
        volume = 1;
        rate = 1;
        constructor(texte: string) {
          this.text = texte;
        }
      },
    );
    vi.stubGlobal(
      'webkitSpeechRecognition',
      class {
        lang = '';
        continuous = false;
        interimResults = false;
        maxAlternatives = 1;
        onresult: ((e: any) => void) | null = null;
        onend: (() => void) | null = null;
        onerror: (() => void) | null = null;
        start() {}
        stop() {}
      },
    );
    vi.spyOn(window.HTMLMediaElement.prototype, 'play').mockRejectedValue(new Error('absent'));
    vi.spyOn(window, 'confirm').mockReturnValue(true);
  });

  afterEach(() => {
    TestBed.resetTestingModule();
    vi.useRealTimers();
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  describe('résolution de l’exercice', () => {
    it('renvoie à la séance quand l’exo n’y est pas', async () => {
      await boot([], 'inconnu');
      expect(router.navigate).toHaveBeenCalledWith(['/session', '7']);
    });

    it('renvoie à la séance quand l’exo n’est pas une sortie en extérieur', async () => {
      const tapis: ExerciceForm = { id: 'a', nom: 'Course', cardioType: 'COURSE', series: [] };
      await boot([tapis], 'a');
      expect(router.navigate).toHaveBeenCalledWith(['/session', '7']);
    });

    it('reste sur la page pour une sortie en extérieur', async () => {
      await boot([exoCourse('a')], 'a');
      expect(router.navigate).not.toHaveBeenCalled();
      expect(component.etat()).toBe('pret');
    });
  });

  describe('déroulé de la course', () => {
    it('n’écoute le GPS qu’une fois démarrée', async () => {
      await boot([exoCourse('a')], 'a');
      expect(component.etat()).toBe('pret');

      component.demarrer();
      emettrePosition(position(0, 0));
      emettrePosition(position(400, 120));

      expect(component.etat()).toBe('enCours');
      expect(component.runtime.distanceM()).toBeCloseTo(400, -1);
    });

    it('met en pause et reprend sur le bouton du casque', async () => {
      await boot([exoCourse('a')], 'a');
      component.demarrer();
      component.basculerPause();
      expect(component.enPause()).toBe(true);
      component.basculerPause();
      expect(component.enCours()).toBe(true);
    });

    it('déplace l’allure cible par pas de cinq secondes au kilomètre', async () => {
      await boot([exoCourse('a')], 'a');
      component.demarrer();
      component.accelererCible();
      expect(component.cibleMinParKm()).toBeCloseTo(6 - 5 / 60, 5);
      component.ralentirCible();
      expect(component.cibleMinParKm()).toBeCloseTo(6, 5);
    });
  });

  describe('annonces vocales', () => {
    it('annonce chaque kilomètre franchi', async () => {
      await boot([exoCourse('a')], 'a');
      component.demarrer();
      paroles.length = 0;

      emettrePosition(position(0, 0));
      emettrePosition(position(1200, 360));
      vi.advanceTimersByTime(1000);

      expect(paroles.some((p) => p.includes('Kilomètre 1'))).toBe(true);
    });

    // WHY: c'est le cas qui casse au retour d'arrière-plan — plusieurs kilomètres tombent d'un
    // tick au suivant et l'athlète entendrait le coach réciter tout ce qu'il a manqué.
    it('n’annonce que le dernier kilomètre quand plusieurs sont franchis d’un coup', async () => {
      await boot([exoCourse('a')], 'a');
      component.demarrer();
      emettrePosition(position(0, 0));
      vi.advanceTimersByTime(1000);
      paroles.length = 0;

      emettrePosition(position(3400, 1020));
      vi.advanceTimersByTime(1000);

      const annonces = paroles.filter((p) => p.includes('Kilomètre'));
      expect(annonces).toHaveLength(1);
      expect(annonces[0]).toContain('Kilomètre 3');
    });

    // WHY: c'est le cœur du changement — la moyenne depuis le départ aurait annoncé 7:00 sur
    // ce scénario, ce qui aurait laissé croire à un kilomètre correct alors qu'il a été couru
    // en dix minutes.
    it('annonce l’allure du kilomètre qui vient d’être bouclé, pas la moyenne', async () => {
      await boot([exoCourse('a')], 'a');
      component.demarrer();
      emettrePosition(position(0, 0));
      emettrePosition(position(1000, 240));
      vi.advanceTimersByTime(1000);
      paroles.length = 0;

      emettrePosition(position(2000, 840));
      vi.advanceTimersByTime(1000);

      const annonce = paroles.join(' ');
      expect(annonce).toContain('Kilomètre 2');
      expect(annonce).toContain('10 minutes 0');
      expect(annonce).not.toContain('7 minutes 0');
    });

    it('se tait sur l’écart d’allure tant qu’il n’a pas duré quinze secondes', async () => {
      await boot([exoCourse('a')], 'a');
      component.demarrer();
      component.fixerCible(4);
      emettrePosition(position(0, 0));
      emettrePosition(position(100, 60));
      paroles.length = 0;

      vi.advanceTimersByTime(5000);
      expect(paroles.filter((p) => p.includes('Accélère'))).toHaveLength(0);

      vi.advanceTimersByTime(15000);
      expect(paroles.filter((p) => p.includes('Accélère'))).toHaveLength(1);
    });

    it('impose une minute de silence entre deux annonces d’allure', async () => {
      await boot([exoCourse('a')], 'a');
      component.demarrer();
      component.fixerCible(4);
      emettrePosition(position(0, 0));
      emettrePosition(position(100, 60));

      vi.advanceTimersByTime(20000);
      vi.advanceTimersByTime(20000);
      expect(paroles.filter((p) => p.includes('Accélère'))).toHaveLength(1);
    });
  });

  describe('fin de course', () => {
    async function courirPuisTerminer() {
      component.demarrer();
      emettrePosition(position(0, 0));
      emettrePosition(position(1000, 300));
      await component.terminer();
    }

    it('téléverse la trace et retient les mesures du serveur', async () => {
      await boot([exoCourse('a')], 'a');
      await courirPuisTerminer();

      expect(chironApi.enregistrerTraceCourse).toHaveBeenCalledOnce();
      const serie = component.exercice!.series[0];
      expect(serie.courseTraceId).toBe(77);
      expect(serie.distanceM).toBe(1000);
      expect(serie.dureeMin).toBe(5);
      expect(serie.allureKmh).toBe(12);
      expect(activeSession.snapshot).toHaveBeenCalled();
    });

    // WHY: perdre le réseau à l'arrivée ne doit pas effacer la sortie. Les mesures du client
    // partent alors dans le journal, sans identifiant de trace.
    it('retombe sur les mesures du client quand le téléversement échoue', async () => {
      await boot([exoCourse('a')], 'a');
      chironApi.enregistrerTraceCourse.mockReturnValue(throwError(() => new Error('hors ligne')));
      component.demarrer();
      emettrePosition(position(0, 0));
      emettrePosition(position(1000, 300));
      await component.terminer();

      expect(component.erreurEnregistrement()).toBe(true);
      const serie = component.exercice!.series[0];
      expect(serie.courseTraceId).toBeNull();
      expect(serie.distanceM).toBeCloseTo(1000, -1);
    });

    it('ne téléverse rien sous deux points', async () => {
      await boot([exoCourse('a')], 'a');
      component.demarrer();
      emettrePosition(position(0, 0));
      await component.terminer();

      expect(chironApi.enregistrerTraceCourse).not.toHaveBeenCalled();
      expect(component.termine()).toBe(true);
    });

    it('purge l’instantané au retour vers la séance', async () => {
      await boot([exoCourse('a')], 'a');
      await courirPuisTerminer();
      component.retourSeance();

      expect(localStorage.getItem(CLE_STOCKAGE_COURSE)).toBeNull();
      expect(router.navigate).toHaveBeenCalledWith(['/session', '7']);
    });
  });

  describe('retour d’arrière-plan', () => {
    // WHY: recharger la page à côté de la course est le scénario réel — Chrome peut la tuer.
    // La distance et l'allure cible doivent revenir, et les kilomètres déjà annoncés ne doivent
    // pas être rejoués.
    it('reprend la course, la cible et les kilomètres déjà annoncés', async () => {
      await boot([exoCourse('a')], 'a');
      component.demarrer();
      component.fixerCible(5);
      emettrePosition(position(0, 0));
      emettrePosition(position(2400, 720));
      const snapshot = JSON.parse(localStorage.getItem(CLE_STOCKAGE_COURSE)!);
      localStorage.setItem(CLE_STOCKAGE_COURSE, JSON.stringify({ ...snapshot, kmAnnonces: 2 }));

      TestBed.resetTestingModule();
      await boot([exoCourse('a')], 'a');

      expect(component.etat()).toBe('enCours');
      expect(component.cibleMinParKm()).toBe(5);
      expect(component.runtime.distanceM()).toBeCloseTo(2400, -1);

      paroles.length = 0;
      vi.advanceTimersByTime(1000);
      expect(paroles.filter((p) => p.includes('Kilomètre'))).toHaveLength(0);
    });

    // WHY: rechargée sur une course en pause, la page n'a plus de boucle de survie. La reprise
    // est le seul geste utilisateur qui reste pour la rétablir, sans quoi la fin de la sortie
    // se ferait geler écran éteint.
    it('remet la voix en route en reprenant une course restaurée en pause', async () => {
      await boot([exoCourse('a')], 'a');
      component.demarrer();
      emettrePosition(position(0, 0));
      component.basculerPause();

      TestBed.resetTestingModule();
      await boot([exoCourse('a')], 'a');
      expect(component.etat()).toBe('enPause');

      paroles.length = 0;
      component.basculerPause();

      expect(component.enCours()).toBe(true);
      // WHY: l'assertion vient du dictionnaire et non d'une chaîne recopiée — c'est le retour
      // de la voix qui est vérifié ici, pas la formulation, qui peut être réécrite.
      const reprise = fr['course.say.resumed'].split('|')[0].trim();
      expect(paroles.join(' ')).toContain(reprise);
    });
  });

  describe('allure cible', () => {
    it('accepte une allure tapée au clavier', async () => {
      await boot([exoCourse('a')], 'a');
      component.cibleSaisie.set('5:30');
      component.validerCibleSaisie();
      expect(component.cibleMinParKm()).toBeCloseTo(5.5, 4);
    });

    it('rétablit la valeur courante sur une saisie illisible', async () => {
      await boot([exoCourse('a')], 'a');
      component.fixerCible(5);
      component.cibleSaisie.set('nawak');
      component.validerCibleSaisie();
      expect(component.cibleMinParKm()).toBeCloseTo(5, 4);
      expect(component.cibleSaisie()).toBe('5:00');
    });

    it('efface la cible sur une saisie vide', async () => {
      await boot([exoCourse('a')], 'a');
      component.fixerCible(5);
      component.cibleSaisie.set('');
      component.validerCibleSaisie();
      expect(component.cibleMinParKm()).toBeNull();
    });
  });

  describe('unité d’allure', () => {
    it('convertit la cible affichée en km/h sans changer la cible réelle', async () => {
      await boot([exoCourse('a')], 'a');
      component.fixerCible(5);
      component.choisirUnite('kmh');

      expect(component.uniteLibelle()).toBe('km/h');
      expect(component.cibleSaisie()).toBe('12,0');
      expect(component.cibleMinParKm()).toBeCloseTo(5, 4);
    });

    it('lit une cible tapée en km/h', async () => {
      await boot([exoCourse('a')], 'a');
      component.choisirUnite('kmh');
      component.cibleSaisie.set('10');
      component.validerCibleSaisie();

      expect(component.cibleMinParKm()).toBeCloseTo(6, 4);
    });

    it('dicte l’allure en kilomètres heure', async () => {
      await boot([exoCourse('a')], 'a');
      component.choisirUnite('kmh');
      component.demarrer();
      emettrePosition(position(0, 0));
      emettrePosition(position(1200, 360));
      vi.advanceTimersByTime(1000);

      const annonce = paroles.join(' ');
      expect(annonce).toContain('Kilomètre 1');
      expect(annonce).toContain('kilomètres heure');
    });

    it('retient l’unité entre deux visites', async () => {
      await boot([exoCourse('a')], 'a');
      component.choisirUnite('kmh');

      TestBed.resetTestingModule();
      await boot([exoCourse('a')], 'a');
      expect(component.uniteAllure()).toBe('kmh');
    });
  });

  describe('volume de la voix', () => {
    it('fait parler la voix au niveau qui vient d’être choisi', async () => {
      await boot([exoCourse('a')], 'a');
      component.changerVolume('40');
      volumes.length = 0;
      component.essayerLaVoix();

      expect(component.volumeVoix()).toBe(40);
      expect(volumes.every((v) => Math.abs(v - 0.4) < 1e-6)).toBe(true);
      expect(volumes.length).toBeGreaterThan(0);
    });

    it('borne le curseur et retient le niveau entre deux visites', async () => {
      await boot([exoCourse('a')], 'a');
      component.changerVolume('500');
      component.essayerLaVoix();
      expect(component.volumeVoix()).toBe(100);

      component.changerVolume('0');
      component.essayerLaVoix();

      TestBed.resetTestingModule();
      await boot([exoCourse('a')], 'a');
      expect(component.volumeVoix()).toBe(10);
    });
  });

  describe('boutons du casque', () => {
    it('retient le mapping choisi entre deux visites', async () => {
      await boot([exoCourse('a')], 'a');
      component.changerMappingCasque('nexttrack', 'distance');

      TestBed.resetTestingModule();
      await boot([exoCourse('a')], 'a');
      expect(component.mappingCasque().nexttrack).toBe('distance');
    });

    it('exécute l’action câblée sur un bouton', async () => {
      await boot([exoCourse('a')], 'a');
      component.demarrer();
      emettrePosition(position(0, 0));
      emettrePosition(position(1000, 300));
      paroles.length = 0;

      web().executer({ nom: 'distance' });
      expect(paroles.some((p) => p.includes('1.00'))).toBe(true);
    });
  });

  describe('chrono', () => {
    it('avance après le départ', async () => {
      await boot([exoCourse('a')], 'a');
      component.demarrer();
      expect(component.chrono()).toBe('00:00');

      vi.advanceTimersByTime(5000);
      expect(component.chrono()).toBe('00:05');

      vi.advanceTimersByTime(60000);
      expect(component.chrono()).toBe('01:05');
    });

    it('se fige en pause et repart à la reprise', async () => {
      await boot([exoCourse('a')], 'a');
      component.demarrer();
      vi.advanceTimersByTime(10000);
      component.basculerPause();

      vi.advanceTimersByTime(30000);
      expect(component.chrono()).toBe('00:10');

      component.basculerPause();
      vi.advanceTimersByTime(5000);
      expect(component.chrono()).toBe('00:15');
    });
  });

  describe('micro depuis le casque', () => {
    // WHY: mediaSession n'envoie qu'une impulsion, sans début ni fin d'appui. L'écoute doit
    // donc se refermer seule, sinon le micro resterait ouvert jusqu'à la fin de la sortie.
    it('ouvre le micro puis le referme au bout du délai', async () => {
      await boot([exoCourse('a')], 'a');
      component.demarrer();

      web().ecouterMainsLibres();
      expect(component.ecoute()).toBe(true);

      vi.advanceTimersByTime(7000);
      expect(component.ecoute()).toBe(false);
    });

    it('referme le micro sur une seconde impulsion', async () => {
      await boot([exoCourse('a')], 'a');
      component.demarrer();

      web().ecouterMainsLibres();
      web().ecouterMainsLibres();
      expect(component.ecoute()).toBe(false);
    });
  });

  describe('commandes vocales', () => {
    it('met la course en pause sur « pause »', async () => {
      await boot([exoCourse('a')], 'a');
      component.demarrer();
      web()['interpreterCommande']('pause');
      expect(component.enPause()).toBe(true);
    });

    it('reprend la course sur « reprends »', async () => {
      await boot([exoCourse('a')], 'a');
      component.demarrer();
      component.basculerPause();
      web()['interpreterCommande']('allez reprends');
      expect(component.enCours()).toBe(true);
    });

    it('annonce la distance sur « distance »', async () => {
      await boot([exoCourse('a')], 'a');
      component.demarrer();
      emettrePosition(position(0, 0));
      emettrePosition(position(1000, 300));
      paroles.length = 0;

      web()['interpreterCommande']('quelle distance');
      expect(paroles.some((p) => p.includes('1.00'))).toBe(true);
    });

    it('le dit quand elle n’a pas compris', async () => {
      await boot([exoCourse('a')], 'a');
      component.demarrer();
      paroles.length = 0;

      web()['interpreterCommande']('bonjour la lune');
      expect(paroles.some((p) => p.includes('Répète'))).toBe(true);
    });
  });
});
