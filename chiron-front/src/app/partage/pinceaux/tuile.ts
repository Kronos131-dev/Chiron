import { Tuile } from '../carte';
import { Zone, tailleAjustee } from '../mise-en-page';
import { Palier } from '../paliers';
import { BRONZE, GRIS, POLICE_CORPS, POLICE_TITRE, BLANC } from './scene';
import { StyleTexte, ecrire, largeurTexte, mesureurDe, rectArrondi } from './texte';

const TAILLE_VALEUR_MAX = 54;
const TAILLE_VALEUR_MIN = 28;
const TAILLE_UNITE = 22;

export function dessinerTuile(
  ctx: CanvasRenderingContext2D,
  zone: Zone,
  tuile: Tuile,
  libelle: string,
  palier: Palier,
  accentuer: boolean,
): void {
  ctx.save();
  rectArrondi(ctx, zone.x, zone.y, zone.largeur, zone.hauteur, 22);
  ctx.fillStyle = 'rgba(4, 6, 10, 0.5)';
  ctx.fill();
  ctx.strokeStyle = accentuer ? palier.bordure : 'rgba(255, 255, 255, 0.07)';
  ctx.lineWidth = 2;
  ctx.stroke();
  ctx.restore();

  const centreX = zone.x + zone.largeur / 2;
  const disponible = zone.largeur - 28;
  const style: Omit<StyleTexte, 'taille' | 'couleur'> = { poids: 900, famille: POLICE_TITRE };
  const mesure = mesureurDe(ctx, style);
  const reserveUnite = tuile.unite ? TAILLE_UNITE + 10 : 0;
  const taille = tailleAjustee(
    tuile.valeur,
    mesure,
    disponible - reserveUnite,
    TAILLE_VALEUR_MAX,
    TAILLE_VALEUR_MIN,
  );
  const styleValeur: StyleTexte = { ...style, taille, couleur: accentuer ? palier.accent : BLANC };
  const largeurValeur = largeurTexte(ctx, tuile.valeur, styleValeur);
  const largeurUnite = tuile.unite
    ? largeurTexte(ctx, tuile.unite, { poids: 800, taille: TAILLE_UNITE, couleur: BRONZE })
    : 0;
  const total = largeurValeur + (tuile.unite ? 8 + largeurUnite : 0);
  const base = zone.y + zone.hauteur * 0.52;
  const debut = centreX - total / 2;

  ecrire(ctx, tuile.valeur, debut, base, styleValeur);
  if (tuile.unite) {
    ecrire(ctx, tuile.unite, debut + largeurValeur + 8, base, {
      poids: 800,
      taille: TAILLE_UNITE,
      couleur: BRONZE,
    });
  }
  ecrire(ctx, libelle.toUpperCase(), centreX, zone.y + zone.hauteur - 24, {
    poids: 800,
    taille: 16,
    famille: POLICE_CORPS,
    couleur: GRIS,
    align: 'center',
    espacement: 3,
  });
}
