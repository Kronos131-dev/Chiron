export interface SeanceJournalBrute {
  id: number;
  titre?: string | null;
  startTime?: string | null;
  endTime?: string | null;
  modeleId?: number | null;
  exercices?: unknown[] | null;
}

export interface SeanceFaite {
  id: number;
  titre: string;
  debut: Date;
  modeleId: number | null;
  nbExercices: number;
}

export interface SemaineJournal {
  cle: string;
  debut: Date;
  seances: SeanceFaite[];
}

const JOURS_PAR_SEMAINE = 7;

export function lundiDe(date: Date): Date {
  const lundi = new Date(date.getFullYear(), date.getMonth(), date.getDate());
  const decalage = (lundi.getDay() + JOURS_PAR_SEMAINE - 1) % JOURS_PAR_SEMAINE;
  lundi.setDate(lundi.getDate() - decalage);
  return lundi;
}

function cleDeSemaine(lundi: Date): string {
  const pad = (n: number) => n.toString().padStart(2, '0');
  return `${lundi.getFullYear()}-${pad(lundi.getMonth() + 1)}-${pad(lundi.getDate())}`;
}

export function seancesTerminees(brutes: SeanceJournalBrute[]): SeanceFaite[] {
  const terminees: SeanceFaite[] = [];
  for (const brute of brutes) {
    const nbExercices = brute.exercices?.length ?? 0;
    if (!brute.endTime || nbExercices === 0 || !brute.startTime) continue;
    const debut = new Date(brute.startTime);
    if (Number.isNaN(debut.getTime())) continue;
    terminees.push({
      id: brute.id,
      titre: brute.titre?.trim() ?? '',
      debut,
      modeleId: brute.modeleId ?? null,
      nbExercices,
    });
  }
  return terminees;
}

export function grouperParSemaine(seances: SeanceFaite[]): SemaineJournal[] {
  const parSemaine = new Map<string, SemaineJournal>();
  for (const seance of seances) {
    const lundi = lundiDe(seance.debut);
    const cle = cleDeSemaine(lundi);
    const semaine = parSemaine.get(cle) ?? { cle, debut: lundi, seances: [] };
    semaine.seances.push(seance);
    parSemaine.set(cle, semaine);
  }
  return [...parSemaine.values()]
    .sort((a, b) => b.debut.getTime() - a.debut.getTime())
    .map((semaine) => ({
      ...semaine,
      seances: [...semaine.seances].sort((a, b) => b.debut.getTime() - a.debut.getTime()),
    }));
}
