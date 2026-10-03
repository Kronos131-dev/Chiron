import { Palier } from '../paliers';
import { degradeMetal } from './metal';

const TUILE = [
  [0, 12],
  [0, 5],
  [8, 5],
  [8, 2],
  [24, 2],
  [24, 9],
  [16, 9],
  [16, 11],
  [40, 11],
  [40, 12],
] as const;
const LARGEUR_TUILE = 40;
const HAUTEUR_TUILE = 14;

export function dessinerFrise(
  ctx: CanvasRenderingContext2D,
  x: number,
  y: number,
  largeur: number,
  palier: Palier,
  agrandissement: number,
  opacite: number,
): void {
  const pas = LARGEUR_TUILE * agrandissement;
  ctx.save();
  ctx.beginPath();
  ctx.rect(x, y, largeur, HAUTEUR_TUILE * agrandissement);
  ctx.clip();
  ctx.globalAlpha = opacite;
  ctx.strokeStyle = degradeMetal(ctx, palier, {
    x,
    y,
    largeur,
    hauteur: HAUTEUR_TUILE * agrandissement,
  });
  ctx.lineWidth = 2 * agrandissement;
  ctx.lineJoin = 'miter';
  ctx.beginPath();
  for (let debut = x; debut < x + largeur; debut += pas) {
    TUILE.forEach(([px, py], index) => {
      const cx = debut + px * agrandissement;
      const cy = y + py * agrandissement;
      if (index === 0) ctx.moveTo(cx, cy);
      else ctx.lineTo(cx, cy);
    });
  }
  ctx.stroke();
  ctx.restore();
}
