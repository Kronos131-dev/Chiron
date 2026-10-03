import { Zone, regrouperSplits } from '../mise-en-page';
import { dessinerBarres, dessinerSplitsEnLignes } from '../pinceaux/barres';
import { formaterAllure } from '../../util/allure';
import { dessinerLaurier } from '../pinceaux/laurier';
import { BRONZE, GRIS, POLICE_CORPS, Scene } from '../pinceaux/scene';
import { StyleTexte, ecrire, largeurTexte, mesureurDe, rectArrondi } from '../pinceaux/texte';
import { dessinerTrace } from '../pinceaux/trace';
import { decouperEnLignes, tailleAjustee } from '../mise-en-page';
import { dessinerAccroche, dessinerRangeeTuiles } from './commun';

const RAYON_SCEAU = 74;
const SPLITS_LATERAUX_MAX = 12;

function dessinerSceau(scene: Scene, centreX: number, centreY: number): void {
  const { ctx, palier, t } = scene;
  dessinerLaurier(ctx, centreX, centreY, RAYON_SCEAU, palier, 15);
  const style: StyleTexte = {
    poids: 800,
    taille: 17,
    couleur: BRONZE,
    align: 'center',
    espacement: 3,
  };
  const mesure = (s: string) => largeurTexte(ctx, s, style);
  const lignes = decouperEnLignes(
    t('share.objectifAtteint').toUpperCase(),
    mesure,
    RAYON_SCEAU * 1.5,
    2,
  );
  lignes.forEach((ligne, index) => {
    ecrire(ctx, ligne, centreX, centreY + 6 + (index - (lignes.length - 1) / 2) * 24, style);
  });
}

export function dessinerCourse(scene: Scene, zone: Zone): void {
  const { ctx, carte, format, palier, t } = scene;
  const course = carte.course;
  if (!course) return;
  const story = format.nom === 'story';

  let y = dessinerAccroche(scene, zone, zone.y, t('share.genre.course'));

  const reserveSceau = course.objectifAtteint ? RAYON_SCEAU * 2 + 24 : 0;
  const styleDistance = { poids: 900, espacement: 0 };
  const tailleDistance = tailleAjustee(
    course.distanceKm,
    mesureurDe(ctx, styleDistance),
    zone.largeur - reserveSceau - 120,
    story ? 210 : 176,
    90,
  );
  const largeurDistance = ecrire(ctx, course.distanceKm, zone.x, y + tailleDistance * 0.86, {
    ...styleDistance,
    taille: tailleDistance,
    couleur: palier.accent,
    lueur: palier.lueur,
  });
  ecrire(ctx, 'KM', zone.x + largeurDistance + 16, y + tailleDistance * 0.86, {
    poids: 900,
    taille: tailleDistance * 0.32,
    couleur: BRONZE,
    espacement: 4,
  });
  if (course.objectifAtteint) {
    dessinerSceau(scene, zone.x + zone.largeur - RAYON_SCEAU - 6, y + tailleDistance * 0.5);
  }
  y += tailleDistance * 0.86 + 62;
  ecrire(ctx, carte.titre.toUpperCase(), zone.x, y, {
    poids: 800,
    taille: 24,
    famille: POLICE_CORPS,
    couleur: GRIS,
    espacement: 5,
  });
  y += 30;

  const hauteurTuiles = story ? 150 : 122;
  const lateral = course.splits.length >= 2 && course.splits.length <= SPLITS_LATERAUX_MAX;
  const aDesSplits = course.splits.length >= 2 && !lateral;
  const hauteurSplits = aDesSplits ? (story ? 190 : 112) : 0;
  const bas = zone.y + zone.hauteur;
  let hauteurTrace = bas - y - hauteurTuiles - 24 - hauteurSplits - (aDesSplits ? 24 : 0);
  let splitsVisibles = aDesSplits;
  if (hauteurTrace < format.traceHauteurMin && aDesSplits) {
    splitsVisibles = false;
    hauteurTrace = bas - y - hauteurTuiles - 24;
  }

  if (hauteurTrace > 120) {
    const zoneTrace: Zone = { x: zone.x, y, largeur: zone.largeur, hauteur: hauteurTrace };
    ctx.save();
    rectArrondi(ctx, zoneTrace.x, zoneTrace.y, zoneTrace.largeur, zoneTrace.hauteur, 28);
    ctx.fillStyle = 'rgba(2, 6, 23, 0.5)';
    ctx.fill();
    ctx.strokeStyle = 'rgba(255, 255, 255, 0.07)';
    ctx.lineWidth = 2;
    ctx.stroke();
    ctx.restore();
    if (lateral) {
      const largeurTrace = zoneTrace.largeur * 0.52;
      dessinerTrace(ctx, course.points, { ...zoneTrace, largeur: largeurTrace }, palier);
      const zoneSplits: Zone = {
        x: zoneTrace.x + largeurTrace + 8,
        y: zoneTrace.y + 64,
        largeur: zoneTrace.largeur - largeurTrace - 40,
        hauteur: zoneTrace.hauteur - 88,
      };
      ecrire(ctx, t('course.splits').toUpperCase(), zoneSplits.x, zoneTrace.y + 42, {
        poids: 800,
        taille: 16,
        famille: POLICE_CORPS,
        couleur: GRIS,
        espacement: 4,
      });
      dessinerSplitsEnLignes(
        ctx,
        course.splits.map((split) => ({
          kilometre: split.kilometre,
          allureKmh: split.allureKmh,
          allure: formaterAllure(split.allureKmh),
        })),
        zoneSplits,
        palier,
      );
    } else {
      dessinerTrace(ctx, course.points, zoneTrace, palier);
    }
    y += hauteurTrace + 24;
  }

  y = dessinerRangeeTuiles(scene, zone, y, hauteurTuiles, course.tuiles, 'course.pace');

  if (splitsVisibles) {
    y += 24;
    ecrire(ctx, t('course.splits').toUpperCase(), zone.x, y + 18, {
      poids: 800,
      taille: 18,
      famille: POLICE_CORPS,
      couleur: GRIS,
      espacement: 4,
    });
    const vitesses = regrouperSplits(course.splits.map((split) => split.allureKmh));
    dessinerBarres(
      ctx,
      vitesses,
      { x: zone.x, y: y + 34, largeur: zone.largeur, hauteur: hauteurSplits - 34 },
      palier,
    );
  }
}
