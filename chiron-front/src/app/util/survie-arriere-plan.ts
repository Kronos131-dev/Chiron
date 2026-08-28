import { reglerSessionAudio } from './session-audio';

export type NomStrategie = 'audioElement' | 'webAudio' | 'webLock' | 'wakeLock';

export interface Survie {
  relacher(): void;
  actives(): NomStrategie[];
  audioEnLecture(): boolean;
}

const FREQUENCE_ECHANTILLONNAGE = 8000;
const DUREE_BOUCLE_S = 5;
const AMPLITUDE_INAUDIBLE = 2;
const GAIN_INAUDIBLE = 0.0001;
const FREQUENCE_PORTEUSE_HZ = 40;
const NOM_VERROU = 'chiron-course';

function ecrireAscii(vue: DataView, offset: number, texte: string): void {
  for (let i = 0; i < texte.length; i++) vue.setUint8(offset + i, texte.charCodeAt(i));
}

// WHY: un fichier de silence numérique pur est considéré comme inaudible par Chrome, qui gèle
// alors la page malgré la lecture. Une amplitude minuscule mais non nulle produit une piste
// réellement audible pour le navigateur et inaudible pour l'oreille.
function bouclePresqueSilencieuse(): Blob {
  const nbEchantillons = FREQUENCE_ECHANTILLONNAGE * DUREE_BOUCLE_S;
  const tampon = new ArrayBuffer(44 + nbEchantillons * 2);
  const vue = new DataView(tampon);

  ecrireAscii(vue, 0, 'RIFF');
  vue.setUint32(4, 36 + nbEchantillons * 2, true);
  ecrireAscii(vue, 8, 'WAVE');
  ecrireAscii(vue, 12, 'fmt ');
  vue.setUint32(16, 16, true);
  vue.setUint16(20, 1, true);
  vue.setUint16(22, 1, true);
  vue.setUint32(24, FREQUENCE_ECHANTILLONNAGE, true);
  vue.setUint32(28, FREQUENCE_ECHANTILLONNAGE * 2, true);
  vue.setUint16(32, 2, true);
  vue.setUint16(34, 16, true);
  ecrireAscii(vue, 36, 'data');
  vue.setUint32(40, nbEchantillons * 2, true);

  const periode = FREQUENCE_ECHANTILLONNAGE / FREQUENCE_PORTEUSE_HZ;
  for (let i = 0; i < nbEchantillons; i++) {
    const valeur = Math.sin((i / periode) * 2 * Math.PI) * AMPLITUDE_INAUDIBLE;
    vue.setInt16(44 + i * 2, Math.round(valeur), true);
  }

  return new Blob([tampon], { type: 'audio/wav' });
}

// WHY: c'est le même mécanisme qui maintient la page vivante et qui prend le focus audio.
// Demander « ambient » pour se mêler à la musique revient à dire au système que ce flux est
// interruptible et non prioritaire — il regèle alors la page écran éteint. On ne peut pas
// avoir les deux : la survie prime, et l'athlète peut choisir l'inverse en connaissance.
function survieParAudioElement(melangerAvecLaMusique: boolean): Survie | null {
  if (melangerAvecLaMusique) reglerSessionAudio('ambient');
  const url = URL.createObjectURL(bouclePresqueSilencieuse());
  const element = new Audio(url);
  element.loop = true;
  element.volume = 1;

  let echec: string | null = null;
  element.play().catch((raison) => {
    echec = String(raison?.name ?? raison);
  });

  return {
    relacher: () => {
      element.pause();
      element.src = '';
      URL.revokeObjectURL(url);
    },
    actives: () => ['audioElement'],
    // WHY: `paused` est la seule preuve que la boucle tourne vraiment. Sans elle, un play()
    // refusé passait inaperçu et la page se faisait geler sans que rien ne le signale.
    audioEnLecture: () => !element.paused && echec === null,
  };
}

function survieParWebAudio(): Survie | null {
  const Ctor: typeof AudioContext | undefined =
    (window as any).AudioContext ?? (window as any).webkitAudioContext;
  if (!Ctor) return null;

  reglerSessionAudio('ambient');
  const contexte = new Ctor();
  contexte.resume().catch(() => {});
  const oscillateur = contexte.createOscillator();
  const gain = contexte.createGain();
  gain.gain.value = GAIN_INAUDIBLE;
  oscillateur.frequency.value = FREQUENCE_PORTEUSE_HZ;
  oscillateur.connect(gain);
  gain.connect(contexte.destination);
  oscillateur.start();

  return {
    relacher: () => {
      try {
        oscillateur.stop();
      } catch {}
      contexte.close().catch(() => {});
    },
    actives: () => ['webAudio'],
    audioEnLecture: () => contexte.state === 'running',
  };
}

// WHY: le verrou n'est accordé qu'après un tour de boucle. Un relâchement demandé avant
// l'octroi ne trouverait aucune fonction à appeler et laisserait le verrou tenu pour toujours,
// d'où le drapeau consulté depuis l'intérieur de la tâche.
function survieParWebLock(): Survie | null {
  const verrous = (navigator as any).locks;
  if (!verrous) return null;

  const etat: { liberer: (() => void) | null; relacheDemandee: boolean } = {
    liberer: null,
    relacheDemandee: false,
  };

  verrous
    .request(
      NOM_VERROU,
      { mode: 'exclusive' },
      () =>
        new Promise<void>((resolve) => {
          if (etat.relacheDemandee) {
            resolve();
            return;
          }
          etat.liberer = resolve;
        }),
    )
    .catch(() => {});

  return {
    relacher: () => {
      etat.relacheDemandee = true;
      etat.liberer?.();
      etat.liberer = null;
    },
    actives: () => ['webLock'],
    audioEnLecture: () => false,
  };
}

function survieParWakeLock(): Survie | null {
  const gestionnaire = (navigator as any).wakeLock;
  if (!gestionnaire) return null;

  const etat: { sentinelle: any; relacheDemandee: boolean } = {
    sentinelle: null,
    relacheDemandee: false,
  };

  gestionnaire
    .request('screen')
    .then((obtenue: any) => {
      if (etat.relacheDemandee) {
        obtenue?.release?.().catch(() => {});
        return;
      }
      etat.sentinelle = obtenue;
    })
    .catch(() => {});

  return {
    relacher: () => {
      etat.relacheDemandee = true;
      etat.sentinelle?.release?.().catch(() => {});
      etat.sentinelle = null;
    },
    actives: () => ['wakeLock'],
    audioEnLecture: () => false,
  };
}

const FABRIQUES: Record<NomStrategie, (melanger: boolean) => Survie | null> = {
  audioElement: survieParAudioElement,
  webAudio: survieParWebAudio,
  webLock: survieParWebLock,
  wakeLock: survieParWakeLock,
};

export function strategieDisponible(nom: NomStrategie): boolean {
  if (nom === 'webLock') return !!(navigator as any).locks;
  if (nom === 'wakeLock') return !!(navigator as any).wakeLock;
  if (nom === 'webAudio') {
    return !!((window as any).AudioContext ?? (window as any).webkitAudioContext);
  }
  return true;
}

// WHY: chaque fabrique doit être appelée dans le geste utilisateur qui démarre la course —
// Chrome refuse la lecture audio et le wake lock en dehors d'une interaction.
export function tenirEnVie(strategies: NomStrategie[], melangerAvecLaMusique = false): Survie {
  const actives = strategies
    .map((nom) => FABRIQUES[nom](melangerAvecLaMusique))
    .filter((s): s is Survie => s !== null);

  return {
    relacher: () => actives.forEach((s) => s.relacher()),
    actives: () => actives.flatMap((s) => s.actives()),
    audioEnLecture: () => actives.some((s) => s.audioEnLecture()),
  };
}
