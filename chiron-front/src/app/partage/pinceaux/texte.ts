import { Mesure } from '../mise-en-page';
import { POLICE_TITRE } from './scene';

export interface StyleTexte {
  poids: number;
  taille: number;
  famille?: string;
  couleur: string | CanvasGradient;
  align?: 'left' | 'center' | 'right';
  espacement?: number;
  lueur?: string | null;
}

export function policeDe(style: Pick<StyleTexte, 'poids' | 'taille' | 'famille'>): string {
  return `${style.poids} ${style.taille}px ${style.famille ?? POLICE_TITRE}`;
}

export function largeurTexte(
  ctx: CanvasRenderingContext2D,
  texte: string,
  style: StyleTexte,
): number {
  ctx.font = policeDe(style);
  const espacement = style.espacement ?? 0;
  const caracteres = [...texte];
  if (!espacement) return ctx.measureText(texte).width;
  return (
    caracteres.reduce((somme, caractere) => somme + ctx.measureText(caractere).width, 0) +
    espacement * Math.max(0, caracteres.length - 1)
  );
}

export function ecrire(
  ctx: CanvasRenderingContext2D,
  texte: string,
  x: number,
  y: number,
  style: StyleTexte,
): number {
  const largeur = largeurTexte(ctx, texte, style);
  const align = style.align ?? 'left';
  const debut = align === 'left' ? x : align === 'center' ? x - largeur / 2 : x - largeur;
  ctx.save();
  ctx.font = policeDe(style);
  ctx.fillStyle = style.couleur;
  ctx.textAlign = 'left';
  ctx.textBaseline = 'alphabetic';
  if (style.lueur) {
    ctx.shadowColor = style.lueur;
    ctx.shadowBlur = 18;
  }
  const espacement = style.espacement ?? 0;
  if (!espacement) {
    ctx.fillText(texte, debut, y);
  } else {
    let curseur = debut;
    for (const caractere of texte) {
      ctx.fillText(caractere, curseur, y);
      curseur += ctx.measureText(caractere).width + espacement;
    }
  }
  ctx.restore();
  return largeur;
}

export function mesureurDe(
  ctx: CanvasRenderingContext2D,
  style: Omit<StyleTexte, 'taille' | 'couleur'>,
): Mesure {
  return (texte, taille) => largeurTexte(ctx, texte, { ...style, taille, couleur: '#000' });
}

export function rectArrondi(
  ctx: CanvasRenderingContext2D,
  x: number,
  y: number,
  largeur: number,
  hauteur: number,
  rayon: number,
): void {
  const r = Math.min(rayon, largeur / 2, hauteur / 2);
  ctx.beginPath();
  ctx.moveTo(x + r, y);
  ctx.arcTo(x + largeur, y, x + largeur, y + hauteur, r);
  ctx.arcTo(x + largeur, y + hauteur, x, y + hauteur, r);
  ctx.arcTo(x, y + hauteur, x, y, r);
  ctx.arcTo(x, y, x + largeur, y, r);
  ctx.closePath();
}
