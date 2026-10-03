import { Zone } from '../mise-en-page';
import { dessinerLaurier } from '../pinceaux/laurier';
import { degradeMetal, carre } from '../pinceaux/metal';
import { BLANC, BRONZE, GRIS, Scene } from '../pinceaux/scene';
import { ecrire, largeurTexte, mesureurDe, rectArrondi } from '../pinceaux/texte';
import { dessinerCadreTitreWod } from './wod-entete';
import { dessinerRangeeTuiles } from './commun';
import { formaterNombre } from '../bilan';
import { tailleAjustee } from '../mise-en-page';

const RESERVE_SOUS_CERCLE = 340;
const HAUTEUR_TUILES = 104;
const HAUT_AVANT_CERCLE = 84;

export function dessinerWod(scene: Scene, zone: Zone): void {
  const { ctx, carte, palier, t } = scene;
  const wod = carte.wod;
  if (!wod) return;

  dessinerCadreTitreWod(
    scene,
    zone,
    `${t('share.genre.wod')} · ${carte.titre}`,
    t('share.wodFormat', { min: wod.dureeMin }),
  );

  const diametre = Math.min(
    zone.largeur * 0.72,
    zone.hauteur - HAUT_AVANT_CERCLE - RESERVE_SOUS_CERCLE,
  );
  const centreX = zone.x + zone.largeur / 2;
  const centreY = zone.y + HAUT_AVANT_CERCLE + diametre / 2;
  const rayon = diametre / 2;

  if (wod.recordBattu) dessinerLaurier(ctx, centreX, centreY, rayon + 38, palier, 26);

  const fond = ctx.createRadialGradient(centreX, centreY, rayon * 0.1, centreX, centreY, rayon);
  fond.addColorStop(0, 'rgba(4, 6, 10, 0.9)');
  fond.addColorStop(1, palier.halo);
  ctx.save();
  ctx.fillStyle = fond;
  ctx.beginPath();
  ctx.arc(centreX, centreY, rayon, 0, Math.PI * 2);
  ctx.fill();
  ctx.strokeStyle = degradeMetal(ctx, palier, carre(centreX, centreY, diametre));
  ctx.lineWidth = 14;
  ctx.shadowColor = palier.halo;
  ctx.shadowBlur = 30;
  ctx.beginPath();
  ctx.arc(centreX, centreY, rayon, 0, Math.PI * 2);
  ctx.stroke();
  ctx.shadowColor = 'transparent';
  ctx.strokeStyle = palier.bordure;
  ctx.lineWidth = 2;
  ctx.beginPath();
  ctx.arc(centreX, centreY, rayon - 26, 0, Math.PI * 2);
  ctx.stroke();
  ctx.restore();

  const nombre = String(wod.tours);
  const tailleNombre = tailleAjustee(
    nombre,
    mesureurDe(ctx, { poids: 900 }),
    diametre * 0.62,
    diametre * 0.5,
    80,
  );
  ecrire(ctx, nombre, centreX, centreY + tailleNombre * 0.28, {
    poids: 900,
    taille: tailleNombre,
    couleur: wod.recordBattu ? palier.accent : BLANC,
    align: 'center',
    lueur: palier.lueur,
  });
  ecrire(ctx, t('wod.rounds').toUpperCase(), centreX, centreY + tailleNombre * 0.28 + 52, {
    poids: 800,
    taille: 34,
    couleur: BRONZE,
    align: 'center',
    espacement: 10,
  });

  let y = centreY + rayon + (wod.recordBattu ? 80 : 44);
  const tuiles = wod.mouvements.map((mouvement) => ({
    cle: mouvement.cle,
    valeur: formaterNombre(mouvement.total, scene.langue),
    unite: '',
  }));
  y = dessinerRangeeTuiles(scene, zone, y, HAUTEUR_TUILES, tuiles, null) + 46;
  ecrire(
    ctx,
    t('share.repsTotal', { n: formaterNombre(wod.repsTotal, scene.langue) }).toUpperCase(),
    centreX,
    y,
    {
      poids: 800,
      taille: 30,
      couleur: BRONZE,
      align: 'center',
      espacement: 3,
    },
  );

  if (wod.recordBattu || wod.record !== null) {
    y += 40;
    const texte = (
      wod.recordBattu ? t('wod.newRecord') : t('wod.record', { tours: wod.record ?? 0 })
    ).toUpperCase();
    const largeur =
      largeurTexte(ctx, texte, { poids: 900, taille: 24, couleur: '#000', espacement: 4 }) + 64;
    if (wod.recordBattu) {
      ctx.save();
      rectArrondi(ctx, centreX - largeur / 2, y, largeur, 56, 28);
      ctx.fillStyle = degradeMetal(ctx, palier, {
        x: centreX - largeur / 2,
        y,
        largeur,
        hauteur: 56,
      });
      ctx.fill();
      ctx.restore();
    }
    ecrire(ctx, texte, centreX, y + 37, {
      poids: 900,
      taille: 24,
      couleur: wod.recordBattu ? '#1a1207' : GRIS,
      align: 'center',
      espacement: 4,
    });
  }
}
