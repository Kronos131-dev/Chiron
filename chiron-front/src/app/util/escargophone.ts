const SOUND_URL = '/sounds/den-den-mushi.mp3';
const ANNONCE_URL = (minutes: number) => `/sounds/wod-${minutes}min.mp3`;

const RING_HZ = 520;
const TREMOLO_HZ = 18;
const BURST_MS = 500;
const GAP_MS = 200;

export const VIBRATION_ALARME = [500, 200, 500, 200, 500, 200, 500];

let context: AudioContext | null = null;

function audioContext(): AudioContext | null {
  const Ctor: typeof AudioContext | undefined =
    (window as any).AudioContext ?? (window as any).webkitAudioContext;
  if (!Ctor) return null;
  if (!context) context = new Ctor();
  return context;
}

// WHY: les navigateurs mobiles n'autorisent la lecture audio qu'après un geste de
// l'utilisateur. Le contexte doit donc être créé et repris au moment où l'athlète appuie sur
// « Démarrer », sinon l'alarme reste muette vingt minutes plus tard, hors de tout geste.
export function unlockAudio(): void {
  const ctx = audioContext();
  if (!ctx) return;
  if (ctx.state === 'suspended') ctx.resume().catch(() => {});
}

function sonnerieSynthetisee(): () => void {
  const ctx = audioContext();
  if (!ctx) return () => {};

  const gain = ctx.createGain();
  gain.gain.value = 0;
  gain.connect(ctx.destination);

  const oscillateur = ctx.createOscillator();
  oscillateur.type = 'square';
  oscillateur.frequency.value = RING_HZ;
  oscillateur.connect(gain);

  const tremolo = ctx.createOscillator();
  tremolo.type = 'sine';
  tremolo.frequency.value = TREMOLO_HZ;
  const profondeurTremolo = ctx.createGain();
  profondeurTremolo.gain.value = 0.14;
  tremolo.connect(profondeurTremolo);
  profondeurTremolo.connect(gain.gain);

  oscillateur.start();
  tremolo.start();

  let arrete = false;
  const salve = () => {
    if (arrete) return;
    gain.gain.setValueAtTime(0.16, ctx.currentTime);
    setTimeout(() => {
      if (arrete) return;
      gain.gain.setValueAtTime(0, ctx.currentTime);
      setTimeout(salve, GAP_MS);
    }, BURST_MS);
  };
  salve();

  return () => {
    arrete = true;
    gain.gain.value = 0;
    try {
      oscillateur.stop();
      tremolo.stop();
    } catch {}
    oscillateur.disconnect();
    tremolo.disconnect();
    profondeurTremolo.disconnect();
    gain.disconnect();
  };
}

export function playEscargophone(): () => void {
  let arreterSynthese: (() => void) | null = null;
  let enregistrement: HTMLAudioElement | null = null;
  let annule = false;

  const basculerSurLaSynthese = () => {
    enregistrement = null;
    if (!annule) arreterSynthese = sonnerieSynthetisee();
  };

  try {
    enregistrement = new Audio(SOUND_URL);
    enregistrement.loop = true;
    enregistrement.play().catch(basculerSurLaSynthese);
  } catch {
    basculerSurLaSynthese();
  }

  return () => {
    annule = true;
    if (enregistrement) {
      try {
        enregistrement.pause();
        enregistrement.currentTime = 0;
      } catch {}
      enregistrement = null;
    }
    if (arreterSynthese) {
      arreterSynthese();
      arreterSynthese = null;
    }
  };
}

export function playAnnonce(minutes: number): void {
  try {
    new Audio(ANNONCE_URL(minutes)).play().catch(() => {});
  } catch {}
}

export function vibrer(motif: number | number[]): void {
  if ('vibrate' in navigator) (navigator as any).vibrate(motif);
}
