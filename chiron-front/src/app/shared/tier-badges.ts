/**
 * Mapping niveau de palier (1-8) → chemin de l'image du badge correspondant.
 * Source : backend `PerformanceTier` (Éphèbe → Olympien).
 */
const BADGE_BY_LEVEL: Record<number, string> = {
  1: '/images/badges/ephebe.png',
  2: '/images/badges/argonaute.png',
  3: '/images/badges/hoplite.png',
  4: '/images/badges/myrmidon.png',
  5: '/images/badges/spartiate.png',
  6: '/images/badges/heros.png',
  7: '/images/badges/dieu.png',
  8: '/images/badges/olympien.png',
};

export function tierBadgeUrl(level: number | null | undefined): string {
  const lvl = Math.max(1, Math.min(8, Number(level) || 1));
  return BADGE_BY_LEVEL[lvl];
}
