import { Palier } from '../paliers';
import { carre, degradeMetal } from './metal';

const FEUILLES_PAR_COTE = 13;
const DEPART_BAS = Math.PI * 0.5;
const ARRET_HAUT = -Math.PI * 0.42;

export function dessinerLaurier(
  ctx: CanvasRenderingContext2D,
  centreX: number,
  centreY: number,
  rayon: number,
  palier: Palier,
  tailleFeuille: number,
): void {
  ctx.save();
  ctx.fillStyle = degradeMetal(ctx, palier, carre(centreX, centreY, rayon * 2));
  ctx.strokeStyle = ctx.fillStyle;
  ctx.shadowColor = 'rgba(0, 0, 0, 0.55)';
  ctx.shadowBlur = 6;
  ctx.shadowOffsetY = 2;

  for (const cote of [1, -1]) {
    ctx.lineWidth = Math.max(2, tailleFeuille * 0.12);
    ctx.beginPath();
    for (let i = 0; i <= FEUILLES_PAR_COTE * 2; i++) {
      const angle = DEPART_BAS + ((ARRET_HAUT - DEPART_BAS) * i) / (FEUILLES_PAR_COTE * 2);
      const x = centreX + cote * Math.cos(angle) * rayon;
      const y = centreY + Math.sin(angle) * rayon;
      if (i === 0) ctx.moveTo(x, y);
      else ctx.lineTo(x, y);
    }
    ctx.stroke();

    for (let i = 0; i < FEUILLES_PAR_COTE; i++) {
      const angle = DEPART_BAS + ((ARRET_HAUT - DEPART_BAS) * (i + 0.5)) / FEUILLES_PAR_COTE;
      const x = centreX + cote * Math.cos(angle) * rayon;
      const y = centreY + Math.sin(angle) * rayon;
      const tangente = Math.atan2(Math.cos(angle) * rayon, -cote * Math.sin(angle) * rayon);
      const rotation = cote * tangente + (i % 2 === 0 ? 0.55 : -0.55) * cote;
      ctx.beginPath();
      ctx.ellipse(x, y, tailleFeuille, tailleFeuille * 0.42, rotation, 0, Math.PI * 2);
      ctx.fill();
    }
  }
  ctx.restore();
}
