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
// ponctuation avant d'être reconnu, sinon la moitié des formulations tombe à côté.
export function normaliser(transcript: string): string {
  return transcript
    .toLowerCase()
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/[.,!?;:]/g, ' ')
    .replace(/['\u2019]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();
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

const PLUS_VITE = /(plus vite|accelere|acceler|augmente|monte le rythme|faster|speed up)/;
const MOINS_VITE = /(moins vite|ralenti|ralentis|baisse le rythme|calme|slower|slow down|ease)/;
const PAUSE = /(pause|arrete|arret|stoppe|stop|attends|halte)/;
const REPRENDRE = /(repren|reprend|repart|c est reparti|on y va|continue|resume|go|restart)/;
const CIBLE = /(cible|objectif|vise|passe a|mets? moi a|regle|target|set)/;
const ALLURE = /(allure|rythme|vitesse|pace|tempo)/;
const DISTANCE = /(distance|combien.*(parcouru|fait|km|kilometre)|how far)/;
const DUREE = /(duree|depuis combien|temps|chrono|time|how long)/;
const BILAN = /(bilan|resume|ou j en suis|status|recap)/;

// WHY: l'ordre compte. « passe à cinq minutes trente » contient « allure » dans certaines
// formulations, et « reprends l'allure » contient les deux : la commande la plus spécifique
// doit être reconnue avant la plus générale, sinon l'athlète obtient une annonce au lieu d'un
// réglage.
export function interpreter(transcript: string): Commande | null {
  const t = normaliser(transcript);
  if (!t) return null;

  if (CIBLE.test(t)) {
    const cible = lireAllure(t);
    if (cible !== null) return { nom: 'cible', cibleMinParKm: cible };
  }

  if (
    ALLURE.test(t) &&
    /\d|zero|un|deux|trois|quatre|cinq|six|sept|huit|neuf|dix/.test(motsEnNombres(t))
  ) {
    const cible = lireAllure(t);
    if (cible !== null) return { nom: 'cible', cibleMinParKm: cible };
  }

  if (PLUS_VITE.test(t)) return { nom: 'plusVite' };
  if (MOINS_VITE.test(t)) return { nom: 'moinsVite' };
  if (REPRENDRE.test(t)) return { nom: 'reprendre' };
  if (PAUSE.test(t)) return { nom: 'pause' };
  if (BILAN.test(t)) return { nom: 'bilan' };
  if (DISTANCE.test(t)) return { nom: 'distance' };
  if (DUREE.test(t)) return { nom: 'duree' };
  if (ALLURE.test(t)) return { nom: 'allure' };

  return null;
}
