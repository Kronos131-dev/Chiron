import { Palier } from '../paliers';
import { Zone } from '../mise-en-page';

export function degradeMetal(
  ctx: CanvasRenderingContext2D,
  palier: Palier,
  zone: Zone,
): CanvasGradient {
  const degrade = ctx.createLinearGradient(
    zone.x,
    zone.y,
    zone.x + zone.largeur,
    zone.y + zone.hauteur,
  );
  for (const [position, couleur] of palier.metal) degrade.addColorStop(position, couleur);
  return degrade;
}

export function carre(centreX: number, centreY: number, cote: number): Zone {
  return { x: centreX - cote / 2, y: centreY - cote / 2, largeur: cote, hauteur: cote };
}

function composantes(hex: string): [number, number, number] {
  const propre = hex.replace('#', '');
  return [0, 2, 4].map((debut) => parseInt(propre.slice(debut, debut + 2), 16)) as [
    number,
    number,
    number,
  ];
}

export function melanger(depart: string, arrivee: string, ratio: number): string {
  const a = composantes(depart);
  const b = composantes(arrivee);
  const borne = Math.min(1, Math.max(0, ratio));
  const [r, g, bleu] = a.map((valeur, index) => Math.round(valeur + (b[index] - valeur) * borne));
  return `rgb(${r}, ${g}, ${bleu})`;
}
