export type ActionCasque =
  | 'rien'
  | 'ecouter'
  | 'pause'
  | 'allure'
  | 'distance'
  | 'duree'
  | 'bilan'
  | 'plusVite'
  | 'moinsVite';

export type BoutonCasque = 'play' | 'nexttrack' | 'previoustrack' | 'seekforward' | 'seekbackward';

export type MappingCasque = Record<BoutonCasque, ActionCasque>;

export interface Telecommande {
  annoncerEnCours(titre: string, etat: 'playing' | 'paused'): void;
  relacher(): void;
}

export const BOUTONS_CASQUE: BoutonCasque[] = [
  'play',
  'nexttrack',
  'previoustrack',
  'seekforward',
  'seekbackward',
];

export const ACTIONS_CASQUE: ActionCasque[] = [
  'rien',
  'ecouter',
  'pause',
  'allure',
  'distance',
  'duree',
  'bilan',
  'plusVite',
  'moinsVite',
];

export const MAPPING_PAR_DEFAUT: MappingCasque = {
  play: 'pause',
  nexttrack: 'ecouter',
  previoustrack: 'moinsVite',
  seekforward: 'allure',
  seekbackward: 'bilan',
};

const ACTIONS_INERTES: Telecommande = { annoncerEnCours: () => {}, relacher: () => {} };

function sessionMedia(): any | null {
  const nav = navigator as any;
  return nav.mediaSession ?? null;
}

export function telecommandeDisponible(): boolean {
  return sessionMedia() !== null;
}

// WHY: les boutons du casque ne sont routés vers la page que si le navigateur la considère
// comme lisant un média — la boucle audio de survie-arriere-plan est ce qui rend cette session
// visible au système. Sans elle, les handlers sont posés mais jamais appelés.
export function brancherTelecommande(
  mapping: MappingCasque,
  executer: (action: ActionCasque) => void,
): Telecommande {
  const session = sessionMedia();
  if (!session) return ACTIONS_INERTES;

  const posees: string[] = [];

  // WHY: « play » et « pause » sont deux évènements distincts pour un seul bouton physique.
  // Les deux portent donc l'action configurée sur ce bouton, sinon la reprise reste morte.
  const brancher = (evenement: string, action: ActionCasque) => {
    if (action === 'rien') return;
    try {
      session.setActionHandler(evenement, () => executer(action));
      posees.push(evenement);
    } catch {}
  };

  brancher('play', mapping.play);
  brancher('pause', mapping.play);
  brancher('nexttrack', mapping.nexttrack);
  brancher('previoustrack', mapping.previoustrack);
  brancher('seekforward', mapping.seekforward);
  brancher('seekbackward', mapping.seekbackward);

  return {
    annoncerEnCours: (titre, etat) => {
      try {
        if (typeof MediaMetadata !== 'undefined') {
          session.metadata = new MediaMetadata({ title: titre, artist: 'Chiron' });
        }
        session.playbackState = etat;
      } catch {}
    },
    relacher: () => {
      for (const nom of posees) {
        try {
          session.setActionHandler(nom, null);
        } catch {}
      }
      try {
        session.playbackState = 'none';
        session.metadata = null;
      } catch {}
    },
  };
}
