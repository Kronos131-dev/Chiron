import { CarteSeance, Traducteur } from '../carte';
import { Format } from '../mise-en-page';
import { Palier } from '../paliers';

export type Image2D = HTMLImageElement | ImageBitmap;

export interface Ressources {
  fond: Image2D | null;
  badge: Image2D | null;
  icone: Image2D | null;
}

export interface Scene {
  ctx: CanvasRenderingContext2D;
  format: Format;
  palier: Palier;
  carte: CarteSeance;
  res: Ressources;
  t: Traducteur;
  langue: string;
}

export const POLICE_TITRE = '"Space Grotesk", sans-serif';
export const POLICE_CORPS = '"Manrope", sans-serif';
export const BRONZE = '#ffb779';
export const GRIS = '#94a3b8';
export const BLANC = '#f8fafc';
export const ENCRE = 'rgba(4, 6, 10, 0.5)';

export function dimensions(image: Image2D): { largeur: number; hauteur: number } {
  if ('naturalWidth' in image) return { largeur: image.naturalWidth, hauteur: image.naturalHeight };
  return { largeur: image.width, hauteur: image.height };
}
