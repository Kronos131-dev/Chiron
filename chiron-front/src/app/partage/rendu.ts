import { CarteSeance, Traducteur } from './carte';
import { Format } from './mise-en-page';
import { dessinerCadre } from './pinceaux/cadre';
import { Scene } from './pinceaux/scene';
import { palierDe } from './paliers';
import { chargerRessources } from './ressources';
import { dessinerGabarit } from './gabarits';

const QUALITE_JPEG = 0.92;

export function dessinerCarte(scene: Scene): void {
  const contenu = dessinerCadre(scene);
  dessinerGabarit(scene, contenu);
}

export async function rendreCarte(
  carte: CarteSeance,
  format: Format,
  t: Traducteur,
  langue: string,
): Promise<Blob> {
  const palier = palierDe(carte.athlete.niveau);
  const res = await chargerRessources(
    palier,
    carte.athlete.niveau !== null && carte.athlete.niveau > 0,
  );

  const toile = document.createElement('canvas');
  toile.width = format.largeur;
  toile.height = format.hauteur;
  const ctx = toile.getContext('2d');
  if (!ctx) throw new Error('canvas 2d indisponible');

  dessinerCarte({ ctx, format, palier, carte, res, t, langue });

  return new Promise<Blob>((resolve, reject) => {
    toile.toBlob(
      (blob) => (blob ? resolve(blob) : reject(new Error('export de la carte impossible'))),
      'image/jpeg',
      QUALITE_JPEG,
    );
  });
}
