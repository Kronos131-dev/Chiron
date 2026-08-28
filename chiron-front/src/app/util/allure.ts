const SECONDES_PAR_HEURE = 3600;
const SECONDES_PAR_MINUTE = 60;
const ALLURE_MIN_PAR_KM_MAX = 99;

export function kmhVersMinParKm(kmh: number): number | null {
  if (!kmh || kmh <= 0) return null;
  const minParKm = SECONDES_PAR_HEURE / SECONDES_PAR_MINUTE / kmh;
  return minParKm > ALLURE_MIN_PAR_KM_MAX ? null : minParKm;
}

export function minParKmVersKmh(minParKm: number): number {
  if (!minParKm || minParKm <= 0) return 0;
  return SECONDES_PAR_MINUTE / minParKm;
}

export function formaterAllure(kmh: number): string {
  const minParKm = kmhVersMinParKm(kmh);
  if (minParKm === null) return '—';
  const totalSecondes = Math.round(minParKm * SECONDES_PAR_MINUTE);
  const minutes = Math.floor(totalSecondes / SECONDES_PAR_MINUTE);
  const secondes = totalSecondes % SECONDES_PAR_MINUTE;
  return `${minutes}:${secondes.toString().padStart(2, '0')}`;
}

export function formaterChrono(totalSecondes: number): string {
  const secondes = Math.max(0, Math.floor(totalSecondes));
  const heures = Math.floor(secondes / SECONDES_PAR_HEURE);
  const minutes = Math.floor((secondes % SECONDES_PAR_HEURE) / SECONDES_PAR_MINUTE);
  const reste = secondes % SECONDES_PAR_MINUTE;
  const mm = minutes.toString().padStart(2, '0');
  const ss = reste.toString().padStart(2, '0');
  return heures > 0 ? `${heures}:${mm}:${ss}` : `${mm}:${ss}`;
}

export function formaterDistance(metres: number): string {
  return (metres / 1000).toFixed(2);
}
