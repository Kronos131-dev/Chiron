import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { strategieDisponible, tenirEnVie } from './survie-arriere-plan';

const navigateurMutable = navigator as unknown as Record<string, unknown>;
const fenetreMutable = window as unknown as Record<string, unknown>;

describe('survie-arriere-plan', () => {
  let blobsCrees: Blob[];
  let elementsAudio: Array<{ play: ReturnType<typeof vi.fn>; pause: ReturnType<typeof vi.fn> }>;

  beforeEach(() => {
    blobsCrees = [];
    elementsAudio = [];

    vi.stubGlobal(
      'Audio',
      class {
        loop = false;
        volume = 0;
        src = '';
        play = vi.fn().mockResolvedValue(undefined);
        pause = vi.fn();
        constructor() {
          elementsAudio.push(this as never);
        }
      },
    );

    URL.createObjectURL = vi.fn((blob: Blob) => {
      blobsCrees.push(blob);
      return 'blob:factice';
    });
    URL.revokeObjectURL = vi.fn();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  describe('disponibilité des stratégies', () => {
    it('signale le Web Lock indisponible quand navigator.locks manque', () => {
      const memoire = navigateurMutable['locks'];
      delete navigateurMutable['locks'];

      expect(strategieDisponible('webLock')).toBe(false);

      navigateurMutable['locks'] = memoire;
    });

    it('signale le Web Lock disponible quand navigator.locks existe', () => {
      navigateurMutable['locks'] = { request: vi.fn() };

      expect(strategieDisponible('webLock')).toBe(true);

      delete navigateurMutable['locks'];
    });

    it('signale Web Audio indisponible quand aucun constructeur AudioContext n’existe', () => {
      const memoire = fenetreMutable['AudioContext'];
      delete fenetreMutable['AudioContext'];
      delete fenetreMutable['webkitAudioContext'];

      expect(strategieDisponible('webAudio')).toBe(false);

      fenetreMutable['AudioContext'] = memoire;
    });

    it('considère la boucle audio toujours disponible', () => {
      expect(strategieDisponible('audioElement')).toBe(true);
    });
  });

  describe('boucle audio inaudible', () => {
    it('joue une boucle et la coupe au relâchement', () => {
      const survie = tenirEnVie(['audioElement']);

      expect(elementsAudio).toHaveLength(1);
      expect(elementsAudio[0].play).toHaveBeenCalled();

      survie.relacher();

      expect(elementsAudio[0].pause).toHaveBeenCalled();
      expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:factice');
    });

    // WHY: un silence numérique pur laisse Chrome geler la page. Le test verrouille donc le fait
    // que la piste porte un signal non nul, ce qui est toute la raison d'être du fichier.
    it('produit un WAV dont les échantillons ne sont pas tous nuls', async () => {
      tenirEnVie(['audioElement']);

      expect(blobsCrees).toHaveLength(1);
      const octets = new Uint8Array(await blobsCrees[0].arrayBuffer());
      const entete = String.fromCharCode(...octets.slice(0, 4));
      const echantillons = octets.slice(44);

      expect(entete).toBe('RIFF');
      expect(echantillons.some((o) => o !== 0)).toBe(true);
    });
  });

  describe('Web Lock', () => {
    function stubberVerrous() {
      let tacheEnCours: (() => Promise<void>) | null = null;
      const request = vi.fn((_nom: string, _options: unknown, tache: () => Promise<void>) => {
        tacheEnCours = tache;
        return Promise.resolve();
      });
      navigateurMutable['locks'] = { request };
      return { request, accorder: () => tacheEnCours!() };
    }

    it('demande un verrou exclusif et le relâche', async () => {
      const { request, accorder } = stubberVerrous();
      const survie = tenirEnVie(['webLock']);

      expect(request).toHaveBeenCalledWith(
        'chiron-course',
        { mode: 'exclusive' },
        expect.any(Function),
      );

      const tenu = accorder();
      let relache = false;
      void tenu.then(() => {
        relache = true;
      });

      await Promise.resolve();
      expect(relache).toBe(false);

      survie.relacher();
      await tenu;
      expect(relache).toBe(true);

      delete navigateurMutable['locks'];
    });

    // WHY: le verrou est accordé de façon asynchrone. Sans le drapeau interne, un relâchement
    // demandé avant l'octroi ne trouve rien à appeler et le verrou reste tenu indéfiniment.
    it('relâche un verrou accordé après que le relâchement a été demandé', async () => {
      const { accorder } = stubberVerrous();
      const survie = tenirEnVie(['webLock']);

      survie.relacher();
      const tenu = accorder();

      await expect(tenu).resolves.toBeUndefined();

      delete navigateurMutable['locks'];
    });
  });

  describe('composition', () => {
    it('ignore une stratégie non supportée sans casser les autres', () => {
      const memoire = fenetreMutable['AudioContext'];
      delete fenetreMutable['AudioContext'];
      delete fenetreMutable['webkitAudioContext'];

      const survie = tenirEnVie(['webAudio', 'audioElement']);

      expect(elementsAudio).toHaveLength(1);
      expect(() => survie.relacher()).not.toThrow();

      fenetreMutable['AudioContext'] = memoire;
    });

    it('ne fait rien quand aucune stratégie n’est demandée', () => {
      const survie = tenirEnVie([]);

      expect(elementsAudio).toHaveLength(0);
      expect(() => survie.relacher()).not.toThrow();
    });
  });
});
