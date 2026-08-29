export interface Glissement {
  detacher(): void;
}

const DISTANCE_MIN_PX = 70;
const RATIO_HORIZONTAL = 1.8;
const DUREE_MAX_MS = 700;
const BORD_IGNORE_PX = 28;

const BALISES_IGNOREES = new Set(['INPUT', 'SELECT', 'TEXTAREA']);

// WHY: un glissement qui part du bord de l'écran appartient au geste de retour d'Android, et
// un glissement qui part d'un curseur ou d'une zone qui défile horizontalement appartient à
// cet élément. Les voler ferait changer de page pendant qu'on règle un volume.
function gesteRefuse(cible: EventTarget | null, x: number, largeur: number): boolean {
  if (x < BORD_IGNORE_PX || x > largeur - BORD_IGNORE_PX) return true;

  let element = cible instanceof Element ? cible : null;
  while (element) {
    if (BALISES_IGNOREES.has(element.tagName)) return true;
    if (element.hasAttribute('data-sans-glissement')) return true;
    if (element.scrollWidth > element.clientWidth + 1) {
      const debordement = getComputedStyle(element).overflowX;
      if (debordement === 'auto' || debordement === 'scroll') return true;
    }
    element = element.parentElement;
  }
  return false;
}

export function brancherGlissement(
  document: Document,
  changerDePage: (sens: 1 | -1) => void,
): Glissement {
  let departX = 0;
  let departY = 0;
  let departMs = 0;
  let valide = false;

  const commencer = (evenement: TouchEvent) => {
    valide = false;
    if (evenement.touches.length !== 1) return;
    const doigt = evenement.touches[0];
    if (gesteRefuse(evenement.target, doigt.clientX, window.innerWidth)) return;
    departX = doigt.clientX;
    departY = doigt.clientY;
    departMs = Date.now();
    valide = true;
  };

  const terminer = (evenement: TouchEvent) => {
    if (!valide) return;
    valide = false;
    const doigt = evenement.changedTouches[0];
    if (!doigt) return;

    const ecartX = doigt.clientX - departX;
    const ecartY = doigt.clientY - departY;
    if (Date.now() - departMs > DUREE_MAX_MS) return;
    if (Math.abs(ecartX) < DISTANCE_MIN_PX) return;
    if (Math.abs(ecartX) < Math.abs(ecartY) * RATIO_HORIZONTAL) return;

    changerDePage(ecartX < 0 ? 1 : -1);
  };

  const annuler = () => {
    valide = false;
  };

  document.addEventListener('touchstart', commencer, { passive: true });
  document.addEventListener('touchend', terminer, { passive: true });
  document.addEventListener('touchcancel', annuler, { passive: true });

  return {
    detacher: () => {
      document.removeEventListener('touchstart', commencer);
      document.removeEventListener('touchend', terminer);
      document.removeEventListener('touchcancel', annuler);
    },
  };
}
