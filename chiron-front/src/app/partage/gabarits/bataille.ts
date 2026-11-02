import { DispositionPuces, Zone, disposerPuces, tronquerParHauteur } from '../mise-en-page';
import { dessinerCoeur } from '../pinceaux/icones';
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

interface Densite {
  nom: number;
  puce: number;
  hauteurPuce: number;
  ecartPuce: number;
  ecartLignes: number;
  hauteurNom: number;
  marge: number;
}

const DENSITES: Densite[] = [
  { nom: 42, puce: 34, hauteurPuce: 64, ecartPuce: 16, ecartLignes: 14, hauteurNom: 62, marge: 38 },
  { nom: 36, puce: 30, hauteurPuce: 56, ecartPuce: 14, ecartLignes: 12, hauteurNom: 52, marge: 30 },
  { nom: 32, puce: 26, hauteurPuce: 48, ecartPuce: 12, ecartLignes: 10, hauteurNom: 46, marge: 24 },
  { nom: 28, puce: 22, hauteurPuce: 38, ecartPuce: 10, ecartLignes: 8, hauteurNom: 38, marge: 14 },
];

const HAUTEUR_SUITE = 44;
const LARGEUR_NUMERO = 78;
const PADDING_PUCE = 20;
const PUCES_LIGNES_MAX = 3;
const HAUTEUR_SOUS_DETAIL = 30;

interface LigneMesuree {
  ligne: LigneExercice;
  largeurs: number[];
  puces: DispositionPuces;
  hauteur: number;
}

function stylePuce(densite: Densite): StyleTexte {
  return { poids: 800, taille: densite.puce, couleur: BLANC };
}

function hauteurDeLigne(ligne: LigneExercice, lignesDePuces: number, densite: Densite): number {
  const base = densite.hauteurNom + densite.marge;
  if (ligne.genre !== 'muscu') return base + (ligne.sousDetail ? HAUTEUR_SOUS_DETAIL : 0);
  const puces =
    lignesDePuces * densite.hauteurPuce + Math.max(0, lignesDePuces - 1) * densite.ecartLignes;
  return base + puces;
}

function mesurerLignes(scene: Scene, zone: Zone, densite: Densite): LigneMesuree[] {
  const { ctx, carte } = scene;
  const style = stylePuce(densite);
  const largeurPuce = (texte: string) => largeurTexte(ctx, texte, style) + 2 * PADDING_PUCE;
  return (carte.bataille?.lignes ?? []).map((ligne) => {
    const largeurs = ligne.series.map((serie) => largeurPuce(serie.texte));
    const puces = disposerPuces(
      largeurs,
      zone.largeur - LARGEUR_NUMERO,
      densite.ecartPuce,
      PUCES_LIGNES_MAX,
      (masquees) => largeurPuce(`+${masquees}`),
    );
    const lignesDePuces = ligne.series.length ? puces.lignes.length : 0;
    return { ligne, largeurs, puces, hauteur: hauteurDeLigne(ligne, lignesDePuces, densite) };
  });
}

function choisirDensite(
  scene: Scene,
  zone: Zone,
  place: number,
): { densite: Densite; mesurees: LigneMesuree[] } {
  for (const densite of DENSITES) {
    const mesurees = mesurerLignes(scene, zone, densite);
    const total = mesurees.reduce((somme, mesuree) => somme + mesuree.hauteur, 0);
    if (total <= place) return { densite, mesurees };
  }
  const serree = DENSITES[DENSITES.length - 1];
  return { densite: serree, mesurees: mesurerLignes(scene, zone, serree) };
}

function dessinerPuce(
  scene: Scene,
  x: number,
  y: number,
  largeur: number,
  densite: Densite,
  texte: string,
  style: 'normale' | 'meilleure' | 'suite',
): void {
  const { ctx, palier } = scene;
  if (style !== 'suite') {
    ctx.save();
    rectArrondi(ctx, x, y, largeur, densite.hauteurPuce, 16);
    ctx.fillStyle = style === 'meilleure' ? palier.halo : 'rgba(255, 255, 255, 0.06)';
    ctx.fill();
    ctx.strokeStyle = style === 'meilleure' ? palier.couleur : 'rgba(255, 255, 255, 0.1)';
    ctx.lineWidth = 2;
    ctx.stroke();
    ctx.restore();
  }
  ecrire(ctx, texte, x + largeur / 2, y + densite.hauteurPuce / 2 + densite.puce * 0.34, {
    poids: 800,
    taille: densite.puce,
    couleur: style === 'meilleure' ? BRONZE : style === 'suite' ? GRIS : BLANC,
    align: 'center',
  });
}

function dessinerPuces(
  scene: Scene,
  zone: Zone,
  y: number,
  mesuree: LigneMesuree,
  densite: Densite,
): void {
  const { ligne, largeurs, puces } = mesuree;
  const debut = zone.x + LARGEUR_NUMERO;
  puces.lignes.forEach((indices, rang) => {
    const yLigne = y + rang * (densite.hauteurPuce + densite.ecartLignes);
    let x = debut;
    indices.forEach((index) => {
      const serie = ligne.series[index];
      dessinerPuce(
        scene,
        x,
        yLigne,
        largeurs[index],
        densite,
        serie.texte,
        serie.meilleure ? 'meilleure' : 'normale',
      );
      x += largeurs[index] + densite.ecartPuce;
    });
    const derniere = rang === puces.lignes.length - 1;
    if (derniere && puces.masquees > 0) {
      const texte = `+${puces.masquees}`;
      const largeur = largeurTexte(scene.ctx, texte, stylePuce(densite)) + 2 * PADDING_PUCE;
      dessinerPuce(scene, x, yLigne, largeur, densite, texte, 'suite');
    }
  });
}

function dessinerLigne(
  scene: Scene,
  zone: Zone,
  y: number,
  mesuree: LigneMesuree,
  densite: Densite,
): void {
  const { ctx, palier } = scene;
  const { ligne } = mesuree;
  const base = y + densite.hauteurNom * 0.72;
  ecrire(ctx, ligne.numero, zone.x, base, {
    poids: 900,
    taille: densite.nom * 0.9,
    couleur: palier.couleur,
  });

  const detail: StyleTexte =
    ligne.genre === 'muscu'
      ? { poids: 800, taille: 24, famille: POLICE_CORPS, couleur: GRIS, align: 'right' }
      : { poids: 800, taille: 30, couleur: BRONZE, align: 'right' };
  const largeurDetail = ligne.detail ? largeurTexte(ctx, ligne.detail, detail) : 0;
  if (ligne.detail) ecrire(ctx, ligne.detail, zone.x + zone.largeur, base, detail);

  const largeurNom = zone.largeur - LARGEUR_NUMERO - largeurDetail - 24;
  const nom = ajusterUneLigne(scene, ligne.nom, largeurNom, 800, densite.nom, 22);
  ecrire(ctx, nom.texte, zone.x + LARGEUR_NUMERO, base, {
    poids: 800,
    taille: nom.taille,
    couleur: BLANC,
  });

  if (ligne.sousDetail) {
    ecrire(ctx, ligne.sousDetail, zone.x + LARGEUR_NUMERO, base + 32, {
      poids: 600,
      taille: 20,
      famille: POLICE_CORPS,
      couleur: GRIS,
    });
  }
  if (ligne.genre === 'muscu' && ligne.series.length) {
    dessinerPuces(scene, zone, y + densite.hauteurNom, mesuree, densite);
  }

  ctx.save();
  ctx.strokeStyle = 'rgba(255, 255, 255, 0.06)';
  ctx.lineWidth = 1;
  ctx.beginPath();
  ctx.moveTo(zone.x, y + mesuree.hauteur - 2);
  ctx.lineTo(zone.x + zone.largeur, y + mesuree.hauteur - 2);
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
  return y + 48;
}

export function dessinerBataille(scene: Scene, zone: Zone): void {
  const { ctx, carte, t } = scene;
  const bataille = carte.bataille;
  if (!bataille) return;

  let y = dessinerAccroche(scene, zone, zone.y, t('share.genre.bataille'));
  y = dessinerTitre(scene, zone, y, carte.titre, 72, 44, 2);
  y += 14;
  y = dessinerRangeeTuiles(scene, zone, y, 108, bataille.tuiles, 'share.stat.duree');
  y += 20;
  y = dessinerActivite(scene, zone, y);
  y = dessinerSeparateurTitre(scene, zone, y, t('share.honneur'));

  const place = zone.y + zone.hauteur - y;
  const { densite, mesurees } = choisirDensite(scene, zone, place);
  const { visibles, masquees } = tronquerParHauteur(
    mesurees.map((mesuree) => mesuree.hauteur),
    place,
    HAUTEUR_SUITE,
  );

  let curseur = y;
  for (const mesuree of mesurees.slice(0, visibles)) {
    dessinerLigne(scene, zone, curseur, mesuree, densite);
    curseur += mesuree.hauteur;
  }
  if (masquees > 0) {
    ecrire(ctx, t('share.autres', { n: masquees }), zone.x + zone.largeur / 2, curseur + 32, {
      poids: 800,
      taille: 26,
      couleur: GRIS,
      align: 'center',
      espacement: 2,
    });
  }
}
