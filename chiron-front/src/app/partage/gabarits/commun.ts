import { decouperEnLignes, tailleAjustee, Zone } from '../mise-en-page';
import { BLANC, POLICE_CORPS, Scene } from '../pinceaux/scene';
import { StyleTexte, ecrire, mesureurDe } from '../pinceaux/texte';
import { Tuile } from '../carte';
import { dessinerTuile } from '../pinceaux/tuile';

export function dessinerAccroche(scene: Scene, zone: Zone, y: number, texte: string): number {
  const { ctx, palier } = scene;
  ecrire(ctx, texte.toUpperCase(), zone.x, y + 28, {
    poids: 800,
    taille: 28,
    couleur: palier.couleur,
    espacement: 8,
    lueur: palier.lueur,
  });
  return y + 28 + 22;
}

export function dessinerTitre(
  scene: Scene,
  zone: Zone,
  y: number,
  texte: string,
  tailleMax: number,
  tailleMin: number,
  lignesMax: number,
): number {
  const { ctx } = scene;
  const titre = texte.toUpperCase();
  const style: Omit<StyleTexte, 'taille' | 'couleur'> = { poids: 900, espacement: 2 };
  const mesure = mesureurDe(ctx, style);
  let taille = tailleMax;
  let lignes = decouperEnLignes(titre, (s) => mesure(s, taille), zone.largeur, lignesMax);
  while (taille > tailleMin && lignes.some((ligne) => ligne.endsWith('…'))) {
    taille -= 2;
    lignes = decouperEnLignes(titre, (s) => mesure(s, taille), zone.largeur, lignesMax);
  }
  const hauteurLigne = taille * 1.08;
  lignes.forEach((ligne, index) => {
    ecrire(ctx, ligne, zone.x, y + taille * 0.86 + index * hauteurLigne, {
      ...style,
      taille,
      couleur: BLANC,
    });
  });
  return y + lignes.length * hauteurLigne + 10;
}

export function dessinerRangeeTuiles(
  scene: Scene,
  zone: Zone,
  y: number,
  hauteur: number,
  tuiles: Tuile[],
  accentuee: string | null,
): number {
  const ecart = 16;
  const largeur = (zone.largeur - ecart * (tuiles.length - 1)) / tuiles.length;
  tuiles.forEach((tuile, index) => {
    dessinerTuile(
      scene.ctx,
      { x: zone.x + index * (largeur + ecart), y, largeur, hauteur },
      tuile,
      scene.t(tuile.cle),
      scene.palier,
      tuile.cle === accentuee,
    );
  });
  return y + hauteur;
}

export function dessinerSeparateurTitre(
  scene: Scene,
  zone: Zone,
  y: number,
  texte: string,
): number {
  const { ctx, palier } = scene;
  const titre = texte.toUpperCase();
  const style: StyleTexte = {
    poids: 800,
    taille: 20,
    famille: POLICE_CORPS,
    couleur: palier.couleur,
    align: 'center',
    espacement: 6,
  };
  const largeurTitre = ecrire(ctx, titre, zone.x + zone.largeur / 2, y + 20, style);
  const demi = largeurTitre / 2 + 24;
  const milieu = zone.x + zone.largeur / 2;
  ctx.save();
  ctx.strokeStyle = palier.bordure;
  ctx.lineWidth = 2;
  ctx.beginPath();
  ctx.moveTo(zone.x, y + 13);
  ctx.lineTo(milieu - demi, y + 13);
  ctx.moveTo(milieu + demi, y + 13);
  ctx.lineTo(zone.x + zone.largeur, y + 13);
  ctx.stroke();
  ctx.restore();
  return y + 52;
}

export function ajusterUneLigne(
  scene: Scene,
  texte: string,
  largeurMax: number,
  poids: number,
  tailleMax: number,
  tailleMin: number,
): { texte: string; taille: number } {
  const style = { poids, espacement: 0 };
  const mesure = mesureurDe(scene.ctx, style);
  const taille = tailleAjustee(texte, mesure, largeurMax, tailleMax, tailleMin);
  const [ligne] = decouperEnLignes(texte, (s) => mesure(s, taille), largeurMax, 1);
  return { texte: ligne ?? texte, taille };
}
