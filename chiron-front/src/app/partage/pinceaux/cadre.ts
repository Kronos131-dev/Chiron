import { Zone, cadreDe } from '../mise-en-page';
import { dessinerFond } from './fond';
import { dessinerFrise } from './frise';
import { dessinerMedaille } from './medaille';
import { degradeMetal } from './metal';
import { BLANC, BRONZE, ENCRE, GRIS, POLICE_CORPS, Scene } from './scene';
import { dessinerTemple } from './temple';
import { StyleTexte, ecrire, largeurTexte, mesureurDe, rectArrondi } from './texte';
import { tailleAjustee } from '../mise-en-page';

const SITE = 'chiron-sanctuaire.fr';
const NIVEAU_OR_CHATOYANT = 8;
const DIAMETRE_MEDAILLE = 100;
const TAILLE_ICONE = 72;

function dessinerEntete(scene: Scene, zone: Zone): void {
  const { ctx, res, palier, carte, langue } = scene;
  if (res.icone) ctx.drawImage(res.icone, zone.x, zone.y + 4, TAILLE_ICONE, TAILLE_ICONE);
  ecrire(ctx, 'CHIRON', zone.x + TAILLE_ICONE + 24, zone.y + 54, {
    poids: 900,
    taille: 40,
    couleur: BRONZE,
    espacement: 12,
  });
  const date = carte.date
    .toLocaleDateString(langue, {
      weekday: 'short',
      day: 'numeric',
      month: 'short',
      year: 'numeric',
    })
    .toUpperCase();
  ecrire(ctx, date, zone.x + zone.largeur, zone.y + 52, {
    poids: 800,
    taille: 24,
    famille: POLICE_CORPS,
    couleur: GRIS,
    align: 'right',
    espacement: 3,
  });
  dessinerFrise(ctx, zone.x, zone.y + 96, zone.largeur, palier, 2, 0.6);
}

function dessinerPanneau(scene: Scene, zone: Zone): void {
  const { ctx, palier } = scene;
  ctx.save();
  rectArrondi(ctx, zone.x, zone.y, zone.largeur, zone.hauteur, 40);
  ctx.fillStyle = ENCRE;
  ctx.shadowColor = 'rgba(0, 0, 0, 0.5)';
  ctx.shadowBlur = 40;
  ctx.shadowOffsetY = 16;
  ctx.fill();
  ctx.shadowColor = 'transparent';
  ctx.strokeStyle = degradeMetal(ctx, palier, zone);
  ctx.globalAlpha = 0.85;
  ctx.lineWidth = 3;
  ctx.stroke();
  ctx.globalAlpha = 0.3;
  ctx.lineWidth = 1.5;
  rectArrondi(ctx, zone.x + 12, zone.y + 12, zone.largeur - 24, zone.hauteur - 24, 30);
  ctx.stroke();
  ctx.restore();
}

function dessinerPied(scene: Scene, zone: Zone): void {
  const { ctx, palier, carte, res, t } = scene;
  const milieu = zone.x + zone.largeur / 2;
  const filet = zone.y + 14;

  ctx.save();
  ctx.strokeStyle = 'rgba(255, 183, 121, 0.3)';
  ctx.lineWidth = 2;
  ctx.beginPath();
  ctx.moveTo(zone.x, filet);
  ctx.lineTo(milieu - 90, filet);
  ctx.moveTo(milieu + 90, filet);
  ctx.lineTo(zone.x + zone.largeur, filet);
  ctx.stroke();
  ctx.restore();
  dessinerTemple(ctx, milieu, filet, 110, 'rgba(255, 183, 121, 0.75)');

  const aUnPalier = carte.athlete.niveau !== null && carte.athlete.niveau > 0;
  const centreY = zone.y + 88;
  let departTexte = zone.x;
  if (aUnPalier) {
    dessinerMedaille(
      ctx,
      res.badge,
      zone.x + DIAMETRE_MEDAILLE / 2,
      centreY,
      DIAMETRE_MEDAILLE,
      palier,
    );
    departTexte = zone.x + DIAMETRE_MEDAILLE + 28;
  }

  const styleNom: Omit<StyleTexte, 'taille' | 'couleur'> = { poids: 800, espacement: 4 };
  const nom = carte.athlete.username.toUpperCase();
  const taille = tailleAjustee(nom, mesureurDe(ctx, styleNom), 470, 40, 26);
  ecrire(ctx, nom, departTexte, centreY - 2, { ...styleNom, taille, couleur: BLANC });

  if (aUnPalier) {
    const libelle = `${(carte.athlete.palier ?? '').toUpperCase()} · ${t('share.niveau', { n: palier.niveau })}`;
    const couleur =
      palier.niveau >= NIVEAU_OR_CHATOYANT
        ? degradeMetal(ctx, palier, { x: departTexte, y: centreY, largeur: 420, hauteur: 30 })
        : palier.couleur;
    ecrire(ctx, libelle, departTexte, centreY + 34, {
      poids: 800,
      taille: 22,
      couleur,
      espacement: 4,
      lueur: palier.lueur,
    });
  }

  const styleSite: StyleTexte = { poids: 600, taille: 26, famille: POLICE_CORPS, couleur: GRIS };
  const largeurSite = largeurTexte(ctx, SITE, styleSite);
  ecrire(ctx, SITE, zone.x + zone.largeur - largeurSite, centreY + 10, styleSite);
}

export function dessinerCadre(scene: Scene): Zone {
  const cadre = cadreDe(scene.format);
  dessinerFond(scene);
  dessinerEntete(scene, cadre.entete);
  dessinerPanneau(scene, cadre.panneau);
  dessinerPied(scene, cadre.pied);
  return cadre.contenu;
}
