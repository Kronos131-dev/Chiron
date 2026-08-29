import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { Glissement, brancherGlissement } from './glissement-pages';

describe('brancherGlissement', () => {
  let sens: (1 | -1)[];
  let glissement: Glissement;
  let zone: HTMLDivElement;

  function doigt(type: string, x: number, y: number, cible: Element): void {
    const evenement: any = new Event(type, { bubbles: true });
    const point = { clientX: x, clientY: y };
    evenement.touches = type === 'touchend' ? [] : [point];
    evenement.changedTouches = [point];
    cible.dispatchEvent(evenement);
  }

  function glisser(depuis: number, vers: number, cible: Element = zone, y = 300): void {
    doigt('touchstart', depuis, y, cible);
    doigt('touchend', vers, y, cible);
  }

  beforeEach(() => {
    vi.useFakeTimers();
    sens = [];
    zone = document.createElement('div');
    document.body.appendChild(zone);
    glissement = brancherGlissement(document, (s) => sens.push(s));
  });

  afterEach(() => {
    glissement.detacher();
    zone.remove();
    vi.useRealTimers();
  });

  it('avance d’une page vers la gauche', () => {
    glisser(500, 300);
    expect(sens).toEqual([1]);
  });

  it('recule d’une page vers la droite', () => {
    glisser(300, 500);
    expect(sens).toEqual([-1]);
  });

  it('ignore un mouvement trop court', () => {
    glisser(300, 350);
    expect(sens).toEqual([]);
  });

  // WHY: sans ce garde, un défilement de la page vers le bas légèrement de travers ferait
  // changer d'onglet en pleine lecture.
  it('ignore un mouvement à dominante verticale', () => {
    doigt('touchstart', 400, 200, zone);
    doigt('touchend', 300, 400, zone);
    expect(sens).toEqual([]);
  });

  it('ignore un mouvement trop lent pour être un geste', () => {
    doigt('touchstart', 500, 300, zone);
    vi.advanceTimersByTime(900);
    doigt('touchend', 300, 300, zone);
    expect(sens).toEqual([]);
  });

  // WHY: la bande des vingt-huit premiers pixels appartient au geste de retour d'Android.
  it('laisse les bords de l’écran au système', () => {
    glisser(10, 300);
    expect(sens).toEqual([]);
    glisser(window.innerWidth - 10, 300);
    expect(sens).toEqual([]);
  });

  it('laisse un curseur régler sa valeur', () => {
    const curseur = document.createElement('input');
    curseur.type = 'range';
    zone.appendChild(curseur);

    glisser(500, 300, curseur);

    expect(sens).toEqual([]);
  });

  it('laisse défiler une zone qui déborde horizontalement', () => {
    const tableau = document.createElement('div');
    tableau.style.overflowX = 'auto';
    Object.defineProperty(tableau, 'scrollWidth', { value: 900 });
    Object.defineProperty(tableau, 'clientWidth', { value: 300 });
    zone.appendChild(tableau);

    glisser(500, 300, tableau);

    expect(sens).toEqual([]);
  });

  it('respecte une zone marquée comme non glissable', () => {
    const carte = document.createElement('div');
    carte.setAttribute('data-sans-glissement', '');
    zone.appendChild(carte);

    glisser(500, 300, carte);

    expect(sens).toEqual([]);
  });

  it('ne réagit plus une fois détaché', () => {
    glissement.detacher();
    glisser(500, 300);
    expect(sens).toEqual([]);
  });
});
