export interface Voix {
  parler(texte: string): void;
  interrompreEtParler(texte: string): void;
  fixerVolume(fraction: number): void;
  taire(): void;
}

const VOLUME = 1;
const DEBIT = 0.92;

// WHY: une voix déjà masculine n'a pas besoin d'être écrasée — la descendre trop la fait
// grogner au lieu de la poser. C'est la voix neutre, choisie faute de mieux, qu'il faut
// vraiment abaisser pour qu'elle cesse de sonner comme une annonce de gare.
const HAUTEUR_MASCULINE = 0.85;
const HAUTEUR_A_DEFAUT = 0.72;

export const RESPIRATION = '|';

const MASCULINS = [
  'thomas',
  'nicolas',
  'paul',
  'daniel',
  'guillaume',
  'mathieu',
  'henri',
  'rémi',
  'remi',
  'male',
  'homme',
  'man',
  'masculin',
];

const FEMININS = ['female', 'femme', 'woman', 'feminin', 'amelie', 'audrey', 'marie', 'julie'];

const HAUTE_QUALITE = ['google', 'neural', 'wavenet', 'enhanced', 'premium', 'natural', 'siri'];

const BASSE_QUALITE = ['compact', 'espeak', 'pico', 'compressed', 'low'];

function synthese(): SpeechSynthesis | null {
  return typeof window !== 'undefined' && window.speechSynthesis ? window.speechSynthesis : null;
}

export function voixDisponible(): boolean {
  return synthese() !== null && typeof SpeechSynthesisUtterance !== 'undefined';
}

function nomDe(v: SpeechSynthesisVoice): string {
  return `${v.name ?? ''} ${v.voiceURI ?? ''}`.toLowerCase();
}

function porte(v: SpeechSynthesisVoice, indice: string): boolean {
  return new RegExp(`\\b${indice}\\b`).test(nomDe(v));
}

export function estMasculine(voix: SpeechSynthesisVoice | null): boolean {
  return voix !== null && MASCULINS.some((i) => porte(voix, i));
}

// WHY: les moteurs embarqués (eSpeak, Pico, « compact ») sonnent comme un répondeur de 1998 ;
// les voix réseau de Google sont d'une autre génération. Un score plutôt qu'un premier-trouvé,
// parce qu'aucun critère seul ne suffit : une voix masculine compacte est moins bonne qu'une
// voix neutre de haute qualité, qu'on rattrapera en descendant la hauteur.
export function noterVoix(v: SpeechSynthesisVoice, langue: string): number {
  const nom = nomDe(v);
  let score = 0;

  if ((v.lang ?? '').toLowerCase().replace('_', '-') === langue.toLowerCase()) score += 40;
  if (MASCULINS.some((i) => porte(v, i))) score += 100;
  if (FEMININS.some((i) => porte(v, i))) score -= 100;
  if (HAUTE_QUALITE.some((i) => nom.includes(i))) score += 35;
  if (BASSE_QUALITE.some((i) => nom.includes(i))) score -= 60;
  if (v.localService === false) score += 25;

  return score;
}

export function choisirVoixGrave(
  voix: SpeechSynthesisVoice[],
  langue: string,
): SpeechSynthesisVoice | null {
  const racine = langue.slice(0, 2).toLowerCase();
  const memeLangue = voix.filter((v) => (v.lang ?? '').slice(0, 2).toLowerCase() === racine);
  const candidates = memeLangue.length ? memeLangue : voix;
  if (!candidates.length) return null;

  return candidates.reduce((meilleure, v) =>
    noterVoix(v, langue) > noterVoix(meilleure, langue) ? v : meilleure,
  );
}

// WHY: l'API Web Speech n'accepte pas de SSML, donc aucun moyen de demander une pause au
// milieu d'une phrase. Deux énoncés successifs en produisent une naturellement : c'est ce qui
// sépare « Kilomètre 3, 15 minutes 42 » débité d'un trait d'une annonce qui respire.
export function creerVoix(langue: string, pendantLaParole?: (parle: boolean) => void): Voix {
  const moteur = synthese();
  if (!moteur || typeof SpeechSynthesisUtterance === 'undefined') {
    return {
      parler: () => {},
      interrompreEtParler: () => {},
      fixerVolume: () => {},
      taire: () => {},
    };
  }

  let volume = VOLUME;

  let choisie: SpeechSynthesisVoice | null = null;

  const rafraichirLaVoix = () => {
    try {
      choisie = choisirVoixGrave(moteur.getVoices() ?? [], langue);
    } catch {}
  };
  rafraichirLaVoix();

  // WHY: getVoices() rend une liste vide au premier appel sur Chrome — le catalogue arrive de
  // façon asynchrone. Sans ce réveil, Chiron parlerait avec la voix par défaut toute la sortie.
  try {
    moteur.addEventListener?.('voiceschanged', rafraichirLaVoix);
  } catch {}

  let enCoursDeParole = 0;

  const signaler = (parle: boolean) => {
    enCoursDeParole += parle ? 1 : -1;
    if (enCoursDeParole < 0) enCoursDeParole = 0;
    pendantLaParole?.(enCoursDeParole > 0);
  };

  const enoncer = (texte: string, viderLaFile: boolean) => {
    if (!texte) return;
    try {
      if (viderLaFile || moteur.paused) moteur.cancel();
      if (!choisie) rafraichirLaVoix();

      const segments = texte
        .split(RESPIRATION)
        .map((s) => s.trim())
        .filter(Boolean);

      for (const segment of segments) {
        const message = new SpeechSynthesisUtterance(segment);
        message.lang = langue;
        message.volume = volume;
        message.rate = DEBIT;
        message.pitch = estMasculine(choisie) ? HAUTEUR_MASCULINE : HAUTEUR_A_DEFAUT;
        if (choisie) message.voice = choisie;

        signaler(true);
        message.onend = () => signaler(false);
        message.onerror = () => signaler(false);
        moteur.speak(message);
      }
    } catch {}
  };

  return {
    parler: (texte) => enoncer(texte, false),
    interrompreEtParler: (texte) => enoncer(texte, true),
    // WHY: le navigateur plafonne le volume d'un énoncé à 1. Le réglage ne peut donc
    // qu'atténuer la voix sous le niveau de la page, jamais la pousser au-dessus.
    fixerVolume: (fraction) => {
      volume = Math.min(1, Math.max(0, fraction));
    },
    taire: () => {
      try {
        moteur.cancel();
      } catch {}
      enCoursDeParole = 0;
      pendantLaParole?.(false);
    },
  };
}
