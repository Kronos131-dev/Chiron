import { Zone } from '../mise-en-page';
import { Scene } from '../pinceaux/scene';
import { GRIS, POLICE_CORPS } from '../pinceaux/scene';
import { ecrire } from '../pinceaux/texte';

export function dessinerCadreTitreWod(
  scene: Scene,
  zone: Zone,
  titre: string,
  format: string,
): void {
  const { ctx, palier } = scene;
  ecrire(ctx, titre.toUpperCase(), zone.x, zone.y + 30, {
    poids: 800,
    taille: 28,
    couleur: palier.couleur,
    espacement: 8,
    lueur: palier.lueur,
  });
  ecrire(ctx, format.toUpperCase(), zone.x + zone.largeur, zone.y + 30, {
    poids: 800,
    taille: 22,
    famille: POLICE_CORPS,
    couleur: GRIS,
    align: 'right',
    espacement: 4,
  });
}
