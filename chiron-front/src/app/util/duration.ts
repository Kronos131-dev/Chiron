/**
 * Helpers de durée de séance, partagés par la page Statistiques et le Journal.
 */

/**
 * Durée d'une séance en minutes entières.
 *
 * @param startIso Heure de début (ISO string) ou null.
 * @param endIso   Heure de fin (ISO string) ou null/undefined.
 * @return Minutes entières si les deux dates sont valides et fin > début, sinon null.
 */
export function durationMinutes(startIso: string | null, endIso?: string | null): number | null {
  if (!startIso || !endIso) return null;
  const start = new Date(startIso).getTime();
  const end = new Date(endIso).getTime();
  if (isNaN(start) || isNaN(end) || end <= start) return null;
  return Math.round((end - start) / 60000);
}

/**
 * Met en forme une durée en minutes (format brut).
 *
 * @param min Durée en minutes, ou null.
 * @return « — » si null/0, sinon « 72 min ».
 */
export function formatDuration(min: number | null): string {
  if (min == null || min <= 0) return '—';
  return `${Math.round(min)} min`;
}
