import { Injectable, signal } from '@angular/core';
import { ExerciceForm } from '../shared/exercise-forms';
import { nowAsLocalDateTime } from '../util/local-date-time';

/** localStorage key under which the in-progress session is persisted. */
const STORAGE_KEY = 'chiron.activeSession';

/** Shape of the session payload persisted to localStorage. */
interface StoredSession {
  routineId: string;
  titre: string;
  exercices: ExerciceForm[];
  startedAt: string | null;
  interactive: boolean;
}

/**
 * Holds the workout session currently being executed so it survives navigating
 * away from the Session page (Coach, Journal, Trésor …) and back.
 *
 * The {@link exercices} signal exposes the very same `ExerciceForm` object
 * references the Session UI mutates in place (reps / charges via `ngModel`), so
 * the in-memory state stays live for as long as the app runs. A localStorage
 * snapshot adds durability across a full app reload (Capacitor Android).
 */
@Injectable({ providedIn: 'root' })
export class ActiveSessionService {
  /** Id of the routine being executed, or `null` when no session is active. */
  readonly routineId = signal<string | null>(null);

  /** Title of the active session, kept in sync with the Session component. */
  readonly titre = signal<string>('');

  /** Exercises of the active session — same references the Session UI mutates. */
  readonly exercices = signal<ExerciceForm[]>([]);

  /** ISO timestamp of when the session was started ("Commencer"), or null. */
  readonly startedAt = signal<string | null>(null);

  // WHY: le caractère interactif n'appartient pas à l'URL. Il n'arrive qu'une fois, dans le
  // `?interactive=true` du départ ; tout retour vers la séance — reprendre depuis la liste,
  // revenir d'un WOD ou d'une course — repasse sans lui, et le micro disparaissait alors de
  // l'écran. Il appartient à la séance en cours, et la suit tant qu'elle dure.
  readonly interactive = signal(false);

  constructor() {
    this.restoreFromStorage();
  }

  /** True when a workout session is currently in progress. */
  hasActiveSession(): boolean {
    return this.routineId() !== null;
  }

  /** True when the in-progress session belongs to the given routine. */
  isActiveFor(routineId: string): boolean {
    return this.routineId() === routineId;
  }

  /**
   * Begin tracking a freshly loaded routine as the active session.
   * Replaces any session previously tracked.
   *
   * @param routineId Identifier of the routine being executed.
   * @param titre     Title of the session.
   * @param exercices Exercise forms — kept by reference, not copied.
   */
  start(routineId: string, titre: string, exercices: ExerciceForm[], interactive = false) {
    this.routineId.set(routineId);
    this.titre.set(titre);
    this.exercices.set(exercices);
    this.startedAt.set(nowAsLocalDateTime());
    this.interactive.set(interactive);
    this.snapshot();
  }

  /** Persist the current in-memory state to localStorage (survives app reloads). */
  snapshot() {
    const id = this.routineId();
    if (id === null) return;

    const data: StoredSession = {
      routineId: id,
      titre: this.titre(),
      exercices: this.exercices(),
      startedAt: this.startedAt(),
      interactive: this.interactive(),
    };
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(data));
    } catch {
      /* storage unavailable / quota exceeded — in-memory state still works */
    }
  }

  /** Drop the active session entirely (e.g. once it has been saved to the journal). */
  clear() {
    this.routineId.set(null);
    this.titre.set('');
    this.exercices.set([]);
    this.startedAt.set(null);
    this.interactive.set(false);
    try {
      localStorage.removeItem(STORAGE_KEY);
    } catch {
      /* ignore */
    }
  }

  /** Reload a previously persisted session — called once on service construction. */
  private restoreFromStorage() {
    let raw: string | null = null;
    try {
      raw = localStorage.getItem(STORAGE_KEY);
    } catch {
      return;
    }
    if (!raw) return;

    try {
      const data = JSON.parse(raw) as StoredSession;
      if (data && data.routineId && Array.isArray(data.exercices)) {
        this.routineId.set(data.routineId);
        this.titre.set(data.titre ?? '');
        this.exercices.set(data.exercices);
        this.startedAt.set(data.startedAt ?? null);
        this.interactive.set(data.interactive === true);
      }
    } catch {
      /* corrupt payload — ignore */
    }
  }
}
