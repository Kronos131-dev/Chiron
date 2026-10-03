import { Zone } from '../mise-en-page';
import { Palier } from '../paliers';
import { BLANC, BRONZE, GRIS, POLICE_CORPS } from './scene';
import { ecrire, rectArrondi } from './texte';

const HAUTEUR_MIN_RELATIVE = 0.3;
const ECART = 8;

export function dessinerBarres(
  ctx: CanvasRenderingContext2D,
  vitesses: number[],
  zone: Zone,
  palier: Palier,
): void {
  if (!vitesses.length) return;
  const min = Math.min(...vitesses);
  const max = Math.max(...vitesses);
  const largeur = Math.min(60, (zone.largeur - ECART * (vitesses.length - 1)) / vitesses.length);
  const total = largeur * vitesses.length + ECART * (vitesses.length - 1);
  const depart = zone.x + (zone.largeur - total) / 2;

  vitesses.forEach((vitesse, index) => {
    const ratio = max > min ? (vitesse - min) / (max - min) : 1;
    const hauteur = zone.hauteur * (HAUTEUR_MIN_RELATIVE + (1 - HAUTEUR_MIN_RELATIVE) * ratio);
    const x = depart + index * (largeur + ECART);
    const plusRapide = vitesse === max && max > min;
    ctx.save();
    ctx.globalAlpha = plusRapide ? 1 : 0.7;
    ctx.fillStyle = plusRapide ? BRONZE : palier.couleur;
    rectArrondi(
      ctx,
      x,
      zone.y + zone.hauteur - hauteur,
      largeur,
      hauteur,
      Math.min(8, largeur / 2),
    );
    ctx.fill();
    ctx.restore();
  });
}

export interface LigneSplit {
  kilometre: number;
  allureKmh: number;
  allure: string;
}

export function dessinerSplitsEnLignes(
  ctx: CanvasRenderingContext2D,
  splits: LigneSplit[],
  zone: Zone,
  palier: Palier,
): void {
  if (!splits.length) return;
  const vitesses = splits.map((split) => split.allureKmh);
  const min = Math.min(...vitesses);
  const max = Math.max(...vitesses);
  const hauteurLigne = Math.min(46, zone.hauteur / splits.length);
  const largeurNumero = 44;
  const largeurAllure = 96;
  const largeurMax = zone.largeur - largeurNumero - largeurAllure - 16;
  const hauteurBarre = Math.min(16, hauteurLigne * 0.38);

  splits.forEach((split, index) => {
    const y = zone.y + index * hauteurLigne;
    const milieu = y + hauteurLigne / 2;
    const ratio = max > min ? (split.allureKmh - min) / (max - min) : 1;
    const plusRapide = split.allureKmh === max && max > min;
    ecrire(ctx, String(split.kilometre), zone.x, milieu + 7, {
      poids: 800,
      taille: 20,
      famille: POLICE_CORPS,
      couleur: GRIS,
    });
    ctx.save();
    ctx.globalAlpha = plusRapide ? 1 : 0.72;
    ctx.fillStyle = plusRapide ? BRONZE : palier.couleur;
    rectArrondi(
      ctx,
      zone.x + largeurNumero,
      milieu - hauteurBarre / 2,
      Math.max(16, largeurMax * (0.35 + 0.65 * ratio)),
      hauteurBarre,
      hauteurBarre / 2,
    );
    ctx.fill();
    ctx.restore();
    ecrire(ctx, split.allure, zone.x + zone.largeur, milieu + 8, {
      poids: 800,
      taille: 22,
      couleur: plusRapide ? BRONZE : BLANC,
      align: 'right',
    });
  });
}
