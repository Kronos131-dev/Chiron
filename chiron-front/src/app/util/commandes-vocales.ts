export type NomCommande =
  | 'allure'
  | 'distance'
  | 'duree'
  | 'bilan'
  | 'pause'
  | 'reprendre'
  | 'plusVite'
  | 'moinsVite'
  | 'cible';

export interface Commande {
  nom: NomCommande;
  cibleMinParKm?: number;
}

const CIBLE_MIN = 2.5;
const CIBLE_MAX = 15;
const SECONDES_PAR_MINUTE = 60;

const NOMBRES: Record<string, number> = {
  zero: 0,
  un: 1,
  une: 1,
  deux: 2,
  trois: 3,
  quatre: 4,
  cinq: 5,
  six: 6,
  sept: 7,
  huit: 8,
  neuf: 9,
  dix: 10,
  onze: 11,
  douze: 12,
  treize: 13,
  quatorze: 14,
  quinze: 15,
  seize: 16,
  vingt: 20,
  trente: 30,
  quarante: 40,
  cinquante: 50,
  one: 1,
  two: 2,
  three: 3,
  four: 4,
  five: 5,
  seven: 7,
  eight: 8,
  nine: 9,
  ten: 10,
  eleven: 11,
  twelve: 12,
  fifteen: 15,
  twenty: 20,
  thirty: 30,
  forty: 40,
  fifty: 50,
};

// WHY: le moteur de Chrome rend « cinq minutes trente », « 5 minutes 30 » ou « 5:30 » selon
// l'humeur du micro et le bruit ambiant. Tout est ramené à une même chaîne sans accent ni
// ponctuation avant d'être reconnu, sinon la moitié des formulations tombe à côté. Le trait
// d'union tombe avec l'apostrophe : le moteur écrit « mets-moi » et « vas-y », que les motifs
// attendent en deux mots.
export function normaliser(transcript: string): string {
  let texte = transcript
    .toLowerCase()
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/[.,!?;:]/g, ' ')
    .replace(/[-'\u2019]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();

  texte = texte.replace(/\bh([aeiouy])/g, '$1');

  return texte;
}

function motsEnNombres(texte: string): string {
  return texte
    .split(' ')
    .map((mot) => (NOMBRES[mot] !== undefined ? String(NOMBRES[mot]) : mot))
    .join(' ');
}

// WHY: « cinq minutes trente » se dicte aussi « cinq trente » ou « cinq et demi ». Les trois
// désignent la même allure, et un coureur essoufflé emploie la plus courte.
export function lireAllure(texte: string): number | null {
  const t = motsEnNombres(texte);

  const etDemi = t.match(/(\d{1,2})\s*(?:min[a-z]*)?\s*et demi/);
  if (etDemi) return borner(parseInt(etDemi[1], 10) + 0.5);

  const separateur = t.match(/(\d{1,2})\s*[:\/h]\s*(\d{1,2})/);
  if (separateur)
    return borner(parseInt(separateur[1], 10) + parseInt(separateur[2], 10) / SECONDES_PAR_MINUTE);

  const avecMinutes = t.match(/(\d{1,2})\s*min[a-z]*\s*(\d{1,2})?/);
  if (avecMinutes) {
    const secondes = avecMinutes[2] ? parseInt(avecMinutes[2], 10) : 0;
    return borner(parseInt(avecMinutes[1], 10) + secondes / SECONDES_PAR_MINUTE);
  }

  const deuxNombres = t.match(/(\d{1,2})\s+(\d{1,2})/);
  if (deuxNombres) {
    return borner(
      parseInt(deuxNombres[1], 10) + parseInt(deuxNombres[2], 10) / SECONDES_PAR_MINUTE,
    );
  }

  const seul = t.match(/(\d{1,2})/);
  if (seul) return borner(parseInt(seul[1], 10));

  return null;
}

function borner(minParKm: number): number | null {
  if (!isFinite(minParKm)) return null;
  if (minParKm < CIBLE_MIN || minParKm > CIBLE_MAX) return null;
  return Math.round(minParKm * SECONDES_PAR_MINUTE) / SECONDES_PAR_MINUTE;
}

// WHY: jumeau de Commandes.java. La cascade « premier motif gagnant » exigeait le mot exact,
// alors que le moteur rend « allur », « pose » ou « billan » — et l'ordre tombait à côté en
// silence. Chaque commande porte donc ses formulations, et c'est la meilleure ressemblance qui
// gagne ; la priorité ne départage que les ex aequo.
interface Motif {
  nom: NomCommande;
  priorite: number;
  formulations: string[];
}

// WHY: `terminer` n'existe que dans le jumeau Java. Clore une sortie à la voix repose sur
// l'archive que le service Android laisse sur le disque, et le navigateur n'a pas d'équivalent :
// l'écart est délibéré, pas un oubli.
const VOCABULAIRE: Motif[] = [
  {
    nom: 'cible',
    priorite: 3,
    formulations: [
      'cible',
      'objectif',
      'vise',
      'passe a',
      'mets moi a',
      'met moi a',
      'regle',
      'target',
      'set',
    ],
  },
  {
    nom: 'plusVite',
    priorite: 2,
    formulations: [
      'plus vite',
      'accelere',
      'acceler',
      'augmente',
      'monte le rythme',
      'faster',
      'speed up',
    ],
  },
  {
    nom: 'moinsVite',
    priorite: 2,
    formulations: [
      'moins vite',
      'ralenti',
      'ralentis',
      'baisse le rythme',
      'calme',
      'slower',
      'slow down',
    ],
  },
  {
    nom: 'reprendre',
    priorite: 2,
    formulations: [
      'reprends',
      'reprend',
      'reprendre',
      'repart',
      'c est reparti',
      'on y va',
      'continue',
      'resume',
      'restart',
    ],
  },
  // WHY: « pause » et « pose » sont homophones en français, et le moteur choisit le mot le plus
  // courant. Deux lettres d'écart sur cinq ne suffisent pas à les rapprocher : la variante est
  // nommée, comme le fait vocabulaire-vocal.ts pour la musculation.
  {
    nom: 'pause',
    priorite: 2,
    formulations: ['pause', 'pose', 'poser', 'arrete', 'stop', 'stoppe', 'attends', 'halte'],
  },
  {
    nom: 'bilan',
    priorite: 2,
    formulations: ['bilan', 'resume', 'ou j en suis', 'status', 'recap', 'le point'],
  },
  {
    nom: 'distance',
    priorite: 2,
    formulations: [
      'distance',
      'combien de kilometres',
      'combien de km',
      'combien j ai parcouru',
      'combien j ai fait',
      'how far',
    ],
  },
  {
    nom: 'duree',
    priorite: 2,
    formulations: ['duree', 'chrono', 'depuis combien de temps', 'combien de temps', 'how long'],
  },
  { nom: 'allure', priorite: 1, formulations: ['allure', 'rythme', 'vitesse', 'pace', 'tempo'] },
];

// WHY: en dessous de cinq caractères la tolérance devient un piège — « sept » est à une lettre de
// « set », et un simple nombre dicté déclencherait un réglage de cible. Les formulations courtes
// ne se reconnaissent qu'à l'identique.
const LONGUEUR_MIN_APPROCHEE = 5;
const SEUIL_RESSEMBLANCE = 0.75;

export function distance(a: string, b: string): number {
  const ligne = Array.from({ length: b.length + 1 }, (_, j) => j);
  for (let i = 1; i <= a.length; i++) {
    let diagonale = ligne[0];
    ligne[0] = i;
    for (let j = 1; j <= b.length; j++) {
      const precedent = ligne[j];
      const cout = a[i - 1] === b[j - 1] ? 0 : 1;
      ligne[j] = Math.min(ligne[j] + 1, ligne[j - 1] + 1, diagonale + cout);
      diagonale = precedent;
    }
  }
  return ligne[b.length];
}

// WHY: le rapport, pas la distance brute. Une lettre fausse sur cinq n'a pas le même poids
// qu'une lettre fausse sur quinze, et un seuil en distance absolue laisse tout passer sur les
// mots longs tout en étranglant les courts.
function rapport(entendu: string, attendu: string): number {
  if (entendu === attendu) return 1;
  if (attendu.length < LONGUEUR_MIN_APPROCHEE) return 0;
  const longueur = Math.max(entendu.length, attendu.length);
  if (longueur === 0) return 0;
  return 1 - distance(entendu, attendu) / longueur;
}

// WHY: une formulation de plusieurs mots se compare à une fenêtre de même longueur, sinon
// « allure » noyée dans une phrase de dix mots ne ressemble plus à rien.
function scorer(texte: string, motif: Motif): number {
  const mots = texte.split(' ');
  let meilleur = 0;
  for (const formulation of motif.formulations) {
    const taille = formulation.split(' ').length;
    for (let i = 0; i + taille <= mots.length; i++) {
      const score = rapport(mots.slice(i, i + taille).join(' '), formulation);
      if (score > meilleur) meilleur = score;
    }
  }
  return meilleur;
}

export function interpreter(transcript: string): Commande | null {
  const texte = normaliser(transcript);
  if (!texte) return null;

  let meilleur: Motif | null = null;
  let meilleurScore = 0;
  for (const motif of VOCABULAIRE) {
    const score = scorer(texte, motif);
    if (score < SEUIL_RESSEMBLANCE) continue;
    if (
      meilleur === null ||
      score > meilleurScore ||
      (score === meilleurScore && motif.priorite > meilleur.priorite)
    ) {
      meilleur = motif;
      meilleurScore = score;
    }
  }
  if (meilleur === null) return null;

  // WHY: « passe à cinq trente » et « allure cinq trente » demandent tous deux un réglage, pas
  // une annonce. C'est la présence d'un nombre lisible comme une allure qui tranche, et elle se
  // teste après coup plutôt que dans l'ordre des motifs.
  if (meilleur.nom === 'cible' || meilleur.nom === 'allure') {
    const cible = /\d/.test(motsEnNombres(texte)) ? lireAllure(texte) : null;
    if (cible !== null) return { nom: 'cible', cibleMinParKm: cible };
    if (meilleur.nom === 'cible') return null;
  }
  return { nom: meilleur.nom };
}
