import { tierBadgeUrl } from '../shared/tier-badges';
import { Palier } from './paliers';
import { Image2D, Ressources } from './pinceaux/scene';

const POLICES = [
  '900 40px "Space Grotesk"',
  '800 30px "Space Grotesk"',
  '700 30px "Space Grotesk"',
  '800 24px "Manrope"',
  '600 24px "Manrope"',
];
const ECHANTILLON = 'AaÉé0123456789';
const ICONE = '/icons/icon-192x192.png';

export async function chargerPolices(): Promise<void> {
  if (!document.fonts) return;
  await Promise.all(POLICES.map((police) => document.fonts.load(police, ECHANTILLON))).catch(
    () => [],
  );
}

async function chargerImage(url: string): Promise<Image2D | null> {
  const image = new Image();
  image.src = url;
  try {
    await image.decode();
    return image;
  } catch {
    return null;
  }
}

export async function chargerRessources(palier: Palier, avecBadge: boolean): Promise<Ressources> {
  const [, fond, badge, icone] = await Promise.all([
    chargerPolices(),
    chargerImage(`/images/themes/bg-${palier.univers}-1440.webp`),
    avecBadge ? chargerImage(tierBadgeUrl(palier.niveau)) : Promise.resolve(null),
    chargerImage(ICONE),
  ]);
  return { fond, badge, icone };
}
