import { Zone, tronquerLignes } from '../mise-en-page';
import { dessinerCoeur } from '../pinceaux/icones';
import { degradeMetal } from '../pinceaux/metal';
import { BLANC, BRONZE, GRIS, POLICE_CORPS, Scene } from '../pinceaux/scene';
import { StyleTexte, ecrire, largeurTexte, rectArrondi } from '../pinceaux/texte';
import { LigneExercice } from '../carte';
import {
  ajusterUneLigne,
  dessinerAccroche,
  dessinerRangeeTuiles,
  dessinerSeparateurTitre,
  dessinerTitre,
} from './commun';

const HAUTEUR_LIGNE = 82;
const HAUTEUR_SUITE = 52;
const HAUTEUR_LIGNE_MAX = 124;
const LARGEUR_NUMERO = 78;
const LARGEUR_JAUGE = 250;

function dessinerLigne(
  scene: Scene,
  zone: Zone,
  rang: number,
  hauteur: number,
  ligne: LigneExercice,
): void {
  const { ctx, palier } = scene;
  const y = rang + (hauteur - HAUTEUR_LIGNE) / 2;
  ecrire(ctx, ligne.numero, zone.x, y + 36, { poids: 900, taille: 30, couleur: palier.couleur });

  const detail: StyleTexte = { poids: 800, taille: 32, couleur: BRONZE, align: 'right' };
  const largeurDetail = largeurTexte(ctx, ligne.detail, detail);
  ecrire(ctx, ligne.detail, zone.x + zone.largeur, y + 36, detail);

  const largeurNom = zone.largeur - LARGEUR_NUMERO - largeurDetail - 24;
  const nom = ajusterUneLigne(scene, ligne.nom, largeurNom, 800, 32, 22);
  ecrire(ctx, nom.texte, zone.x + LARGEUR_NUMERO, y + 36, {
    poids: 800,
    taille: nom.taille,
    couleur: BLANC,
  });

  if (ligne.sousDetail) {
    ecrire(ctx, ligne.sousDetail, zone.x + LARGEUR_NUMERO, y + 66, {
      poids: 600,
      taille: 20,
      famille: POLICE_CORPS,
      couleur: GRIS,
    });
  }
  if (ligne.part > 0) {
    const x = zone.x + zone.largeur - LARGEUR_JAUGE;
    ctx.save();
    ctx.fillStyle = 'rgba(255, 255, 255, 0.08)';
    rectArrondi(ctx, x, y + 58, LARGEUR_JAUGE, 8, 4);
    ctx.fill();
    ctx.fillStyle = degradeMetal(ctx, palier, { x, y: y + 58, largeur: LARGEUR_JAUGE, hauteur: 8 });
    rectArrondi(ctx, x, y + 58, Math.max(8, LARGEUR_JAUGE * ligne.part), 8, 4);
    ctx.fill();
    ctx.restore();
  }
  ctx.save();
  ctx.strokeStyle = 'rgba(255, 255, 255, 0.06)';
  ctx.lineWidth = 1;
  ctx.beginPath();
  ctx.moveTo(zone.x, rang + hauteur - 2);
  ctx.lineTo(zone.x + zone.largeur, rang + hauteur - 2);
  ctx.stroke();
  ctx.restore();
}

function dessinerActivite(scene: Scene, zone: Zone, y: number): number {
  const { ctx, carte, t } = scene;
  const activite = carte.activite;
  if (!activite) return y;
  const style: StyleTexte = { poids: 800, taille: 26, couleur: BLANC };
  const parties: string[] = [];
  if (activite.fcMoyenne) parties.push(t('share.fc', { n: Math.round(activite.fcMoyenne) }));
  if (activite.calories) parties.push(t('share.kcal', { n: Math.round(activite.calories) }));
  const texte = parties.join('   ·   ');
  const largeur = largeurTexte(ctx, texte, style) + (activite.fcMoyenne ? 40 : 0);
  const debut = zone.x + (zone.largeur - largeur) / 2;
  if (activite.fcMoyenne) dessinerCoeur(ctx, debut + 12, y + 18, 24, '#f87171');
  ecrire(ctx, texte, debut + (activite.fcMoyenne ? 40 : 0), y + 28, style);
  return y + 56;
}

export function dessinerBataille(scene: Scene, zone: Zone): void {
  const { ctx, carte, format, t } = scene;
  const bataille = carte.bataille;
  if (!bataille) return;
  const story = format.nom === 'story';

  let y = dessinerAccroche(scene, zone, zone.y, t('share.genre.bataille'));
  y = dessinerTitre(scene, zone, y, carte.titre, story ? 84 : 72, 44, 2);
  y += 14;
  y = dessinerRangeeTuiles(
    scene,
    zone,
    y,
    story ? 150 : 124,
    bataille.tuiles,
    'share.stat.tonnage',
  );
  y += 26;
  y = dessinerActivite(scene, zone, y);
  y = dessinerSeparateurTitre(scene, zone, y, t('share.honneur'));

  const place = zone.y + zone.hauteur - y;
  const { visibles, masquees } = tronquerLignes(
    bataille.lignes,
    place,
    HAUTEUR_LIGNE,
    format.lignesMax,
    HAUTEUR_SUITE,
  );
  const hauteurLigne =
    masquees === 0 && visibles.length > 0
      ? Math.min(HAUTEUR_LIGNE_MAX, Math.max(HAUTEUR_LIGNE, place / visibles.length))
      : HAUTEUR_LIGNE;
  visibles.forEach((ligne, index) =>
    dessinerLigne(scene, zone, y + index * hauteurLigne, hauteurLigne, ligne),
  );
  if (masquees > 0) {
    ecrire(
      ctx,
      t('share.autres', { n: masquees }),
      zone.x + zone.largeur / 2,
      y + visibles.length * hauteurLigne + 40,
      {
        poids: 800,
        taille: 26,
        couleur: GRIS,
        align: 'center',
        espacement: 2,
      },
    );
  }
}
