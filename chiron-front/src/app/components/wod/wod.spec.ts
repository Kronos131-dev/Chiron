import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { signal } from '@angular/core';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { Wod } from './wod';
import { ActiveSessionService } from '../../service/active-session.service';
import { ChironApi } from '../../service/chiron-api';
import { AuthService } from '../../service/auth.service';
import { ExerciceForm } from '../../shared/exercise-forms';

const VINGT_MINUTES_MS = 20 * 60 * 1000;

describe('Wod', () => {
  let component: Wod;
  let fixture: ComponentFixture<Wod>;
  let router: { navigate: ReturnType<typeof vi.fn> };
  let activeSession: {
    exercices: ReturnType<typeof signal<ExerciceForm[]>>;
    snapshot: ReturnType<typeof vi.fn>;
  };
  let exo: ExerciceForm;

  function makeWodExo(id: string, reps: number | null = null): ExerciceForm {
    return {
      id,
      nom: 'Cindy',
      definitionId: 9,
      wodType: 'CINDY',
      series: [{ id: 's1', poids: 0, reps, degressifs: [], dureeMin: 20 }],
    };
  }

  async function boot(exercices: ExerciceForm[], exoId: string) {
    activeSession = {
      exercices: signal<ExerciceForm[]>(exercices),
      snapshot: vi.fn(),
    };
    router = { navigate: vi.fn() };

    await TestBed.configureTestingModule({
      imports: [Wod],
      providers: [
        { provide: Router, useValue: router },
        { provide: ActiveSessionService, useValue: activeSession },
        {
          provide: ChironApi,
          useValue: { getProfile: vi.fn().mockReturnValue({ subscribe: () => {} }) },
        },
        { provide: AuthService, useValue: { getUsername: vi.fn().mockReturnValue('alice') } },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: convertToParamMap({ id: '7', exoId }) } },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(Wod);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  beforeEach(() => {
    vi.useFakeTimers();
    localStorage.clear();
    vi.stubGlobal('AudioContext', undefined);
    vi.stubGlobal('webkitAudioContext', undefined);
    Object.defineProperty(navigator, 'vibrate', { value: vi.fn(), configurable: true });
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    vi.spyOn(window.HTMLMediaElement.prototype, 'play').mockRejectedValue(new Error('absent'));
  });

  afterEach(() => {
    TestBed.resetTestingModule();
    vi.useRealTimers();
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  describe('résolution de l’exercice', () => {
    it('redirige vers le programme quand l’exo n’est pas dans la séance active', async () => {
      await boot([], 'inconnu');
      expect(router.navigate).toHaveBeenCalledWith(['/programme']);
    });

    it('redirige quand l’exo trouvé n’est pas un WOD', async () => {
      const muscu: ExerciceForm = { id: 'a', nom: 'Squat', series: [] };
      await boot([muscu], 'a');
      expect(router.navigate).toHaveBeenCalledWith(['/programme']);
    });

    it('charge la durée et le record depuis l’exo', async () => {
      exo = makeWodExo('a', 14);
      await boot([exo], 'a');

      expect(component.record()).toBe(14);
      expect(component.chrono()).toBe('20:00');
      expect(component.enCours()).toBe(false);
    });
  });

  describe('déroulement de l’AMRAP', () => {
    beforeEach(async () => {
      exo = makeWodExo('a');
      await boot([exo], 'a');
    });

    it('le compteur reste bloqué tant que le chrono n’est pas lancé', () => {
      component.ajouterTour();
      expect(component.tours()).toBe(0);
    });

    it('démarrer lance le décompte', () => {
      component.demarrer();
      expect(component.enCours()).toBe(true);

      vi.advanceTimersByTime(65_000);
      expect(component.chrono()).toBe('18:55');
    });

    it('chaque appui ajoute un tour', () => {
      component.demarrer();
      component.ajouterTour();
      component.ajouterTour();
      component.ajouterTour();

      expect(component.tours()).toBe(3);
    });

    it('le bouton moins retire un tour sans jamais passer sous zéro', () => {
      component.demarrer();
      component.ajouterTour();
      component.retirerTour();
      component.retirerTour();

      expect(component.tours()).toBe(0);
    });

    it('signale la dernière ligne droite dans la dernière minute', () => {
      component.demarrer();
      vi.advanceTimersByTime(VINGT_MINUTES_MS - 30_000);

      expect(component.derniereLigneDroite()).toBe(true);
      expect(component.termine()).toBe(false);
    });
  });

  describe('annonces de temps restant', () => {
    let sonsDemandes: string[];

    beforeEach(async () => {
      sonsDemandes = [];
      class AudioEspion {
        loop = false;
        currentTime = 0;
        constructor(public src: string) {
          sonsDemandes.push(src);
        }
        play() {
          return Promise.resolve();
        }
        pause() {}
      }
      vi.stubGlobal('Audio', AudioEspion);

      exo = makeWodExo('a');
      await boot([exo], 'a');
      component.demarrer();
    });

    function paliersAnnonces(): number[] {
      return sonsDemandes
        .map((url) => /\/sounds\/wod-(\d+)min\.mp3$/.exec(url))
        .filter((m): m is RegExpExecArray => m !== null)
        .map((m) => Number(m[1]));
    }

    function sauterA(minutesEcoulees: number, secondesEnPlus = 0) {
      vi.setSystemTime(component.startedAt()! + minutesEcoulees * 60_000 + secondesEnPlus * 1000);
      vi.advanceTimersByTime(250);
    }

    it('annonce chaque palier au moment où il est franchi', () => {
      vi.advanceTimersByTime(5 * 60_000);
      expect(paliersAnnonces()).toEqual([15]);

      vi.advanceTimersByTime(5 * 60_000);
      expect(paliersAnnonces()).toEqual([15, 10]);

      vi.advanceTimersByTime(5 * 60_000);
      expect(paliersAnnonces()).toEqual([15, 10, 5]);
    });

    it('n’annonce rien avant le premier palier', () => {
      vi.advanceTimersByTime(4 * 60_000);
      expect(paliersAnnonces()).toEqual([]);
    });

    it('n’annonce un palier qu’une seule fois', () => {
      vi.advanceTimersByTime(5 * 60_000);
      vi.advanceTimersByTime(60_000);

      expect(paliersAnnonces()).toEqual([15]);
    });

    it('annonce le palier courant après un retour d’arrière-plan', () => {
      sauterA(15, 2);

      expect(paliersAnnonces()).toEqual([5]);
    });

    it('reste muet quand l’absence a largement dépassé le palier', () => {
      sauterA(16);

      expect(paliersAnnonces()).toEqual([]);
    });

    it('n’annonce plus rien une fois le temps écoulé', () => {
      vi.advanceTimersByTime(VINGT_MINUTES_MS + 60_000);
      sonsDemandes.length = 0;
      vi.advanceTimersByTime(60_000);

      expect(paliersAnnonces()).toEqual([]);
    });
  });

  describe('fin du WOD', () => {
    beforeEach(async () => {
      exo = makeWodExo('a', 10);
      await boot([exo], 'a');
      component.demarrer();
    });

    it('le chrono se fige à 00:00 et l’alarme se déclenche', () => {
      vi.advanceTimersByTime(VINGT_MINUTES_MS + 5_000);

      expect(component.termine()).toBe(true);
      expect(component.chrono()).toBe('00:00');
      expect(component.alarmeEnCours()).toBe(true);
      expect(navigator.vibrate).toHaveBeenCalled();
    });

    it('l’alarme se coupe d’elle-même au bout de quinze secondes', () => {
      vi.advanceTimersByTime(VINGT_MINUTES_MS);
      expect(component.alarmeEnCours()).toBe(true);

      vi.advanceTimersByTime(15_000);
      expect(component.alarmeEnCours()).toBe(false);
    });

    it('plus aucun tour ne peut être ajouté une fois le temps écoulé', () => {
      component.ajouterTour();
      vi.advanceTimersByTime(VINGT_MINUTES_MS);
      component.ajouterTour();

      expect(component.tours()).toBe(1);
    });

    it('signale un nouveau record quand le score dépasse le précédent', () => {
      for (let i = 0; i < 11; i++) component.ajouterTour();
      vi.advanceTimersByTime(VINGT_MINUTES_MS);

      expect(component.recordBattu()).toBe(true);
    });
  });

  describe('retour à la séance', () => {
    beforeEach(async () => {
      exo = makeWodExo('a');
      await boot([exo], 'a');
    });

    it('écrit le score dans la série de l’exo et revient sur la séance', () => {
      component.demarrer();
      component.ajouterTour();
      component.ajouterTour();
      component.retourSeance();

      expect(exo.series[0].reps).toBe(2);
      expect(exo.series[0].poids).toBe(0);
      expect(exo.series[0].dureeMin).toBe(20);
      expect(activeSession.snapshot).toHaveBeenCalled();
      expect(router.navigate).toHaveBeenCalledWith(['/session', '7']);
    });

    it('n’écrase pas le score précédent quand le WOD n’a jamais été lancé', () => {
      exo.series[0].reps = 12;
      component.retourSeance();

      expect(exo.series[0].reps).toBe(12);
      expect(activeSession.snapshot).not.toHaveBeenCalled();
      expect(router.navigate).toHaveBeenCalledWith(['/session', '7']);
    });
  });

  describe('persistance', () => {
    it('restaure le chrono et les tours après un redémarrage de l’app', async () => {
      exo = makeWodExo('a');
      await boot([exo], 'a');
      component.demarrer();
      component.ajouterTour();
      component.ajouterTour();

      vi.advanceTimersByTime(120_000);
      TestBed.resetTestingModule();

      await boot([makeWodExo('a')], 'a');

      expect(component.tours()).toBe(2);
      expect(component.enCours()).toBe(true);
      expect(component.chrono()).toBe('18:00');
    });

    it('ignore un instantané qui concerne un autre exercice', async () => {
      exo = makeWodExo('a');
      await boot([exo], 'a');
      component.demarrer();
      component.ajouterTour();

      TestBed.resetTestingModule();
      await boot([makeWodExo('b')], 'b');

      expect(component.tours()).toBe(0);
      expect(component.enCours()).toBe(false);
    });

    it('purge l’instantané au retour à la séance', async () => {
      exo = makeWodExo('a');
      await boot([exo], 'a');
      component.demarrer();
      component.retourSeance();

      expect(localStorage.getItem('chiron.wod')).toBeNull();
    });
  });
});
