import { Palier } from '../paliers';
import { carre, degradeMetal } from './metal';
import { Image2D } from './scene';

const NIVEAU_AVEC_LUEUR = 6;

export function dessinerMedaille(
  ctx: CanvasRenderingContext2D,
  badge: Image2D | null,
  centreX: number,
  centreY: number,
  diametre: number,
  palier: Palier,
): void {
  const rayon = diametre / 2;
  ctx.save();

  if (palier.niveau >= NIVEAU_AVEC_LUEUR) {
    ctx.shadowColor = palier.halo;
    ctx.shadowBlur = diametre * 0.35;
  }
  ctx.globalAlpha = 0.28;
  ctx.fillStyle = degradeMetal(ctx, palier, carre(centreX, centreY, diametre));
  ctx.beginPath();
  ctx.arc(centreX, centreY, rayon, 0, Math.PI * 2);
  ctx.fill();
  ctx.restore();

  ctx.save();
  ctx.strokeStyle = palier.bordure;
  ctx.lineWidth = 2;
  ctx.beginPath();
  ctx.arc(centreX, centreY, rayon, 0, Math.PI * 2);
  ctx.stroke();
  ctx.globalAlpha = 0.45;
  ctx.strokeStyle = palier.couleur;
  ctx.lineWidth = 3;
  ctx.beginPath();
  ctx.arc(centreX, centreY, rayon - 5, 0, Math.PI * 2);
  ctx.stroke();
  ctx.restore();

  if (!badge) return;
  const cote = diametre * 0.84;
  ctx.save();
  ctx.shadowColor = 'rgba(0, 0, 0, 0.6)';
  ctx.shadowBlur = diametre * 0.08;
  ctx.shadowOffsetY = diametre * 0.04;
  ctx.drawImage(badge, centreX - cote / 2, centreY - cote / 2, cote, cote);
  ctx.restore();
}
