import { Scene, dimensions } from './scene';

const ZOOM_ANTI_FILIGRANE = 1.08;
const PAS_GRILLE = 40;

export function dessinerFond(scene: Scene): void {
  const { ctx, format, palier, res } = scene;
  const { largeur, hauteur } = format;

  ctx.fillStyle = '#020617';
  ctx.fillRect(0, 0, largeur, hauteur);

  if (res.fond) {
    const source = dimensions(res.fond);
    const echelle =
      Math.max(largeur / source.largeur, hauteur / source.hauteur) * ZOOM_ANTI_FILIGRANE;
    ctx.drawImage(res.fond, 0, 0, source.largeur * echelle, source.hauteur * echelle);
  }

  const voile = ctx.createLinearGradient(0, 0, 0, hauteur);
  voile.addColorStop(0, 'rgba(2, 6, 23, 0.42)');
  voile.addColorStop(1, 'rgba(2, 6, 23, 0.92)');
  ctx.fillStyle = voile;
  ctx.fillRect(0, 0, largeur, hauteur);

  const halo = ctx.createRadialGradient(largeur / 2, 0, 0, largeur / 2, 0, largeur * 0.9);
  halo.addColorStop(0, palier.halo);
  halo.addColorStop(1, 'rgba(0, 0, 0, 0)');
  ctx.fillStyle = halo;
  ctx.fillRect(0, 0, largeur, hauteur);

  ctx.strokeStyle = 'rgba(255, 255, 255, 0.02)';
  ctx.lineWidth = 1;
  ctx.beginPath();
  for (let x = 0; x <= largeur; x += PAS_GRILLE) {
    ctx.moveTo(x, 0);
    ctx.lineTo(x, hauteur);
  }
  for (let y = 0; y <= hauteur; y += PAS_GRILLE) {
    ctx.moveTo(0, y);
    ctx.lineTo(largeur, y);
  }
  ctx.stroke();
}
