import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, ActivatedRoute } from '@angular/router';
import { ChironApi, ExerciceDefinitionDto } from '../../service/chiron-api';
import { AuthService } from '../../service/auth.service';
import { HeaderComponent } from '../shared/header/header';
import { ExerciceCardComponent } from '../shared/exercice-card/exercice-card';
import { ExercisePickerComponent } from '../shared/exercise-picker/exercise-picker';
import {
  ExerciceForm,
  generateFormId,
  makeEmptyExercice,
} from '../../shared/exercise-forms';

// Re-export so existing imports (e.g. tests) keep working until they're migrated.
export type { DegressifForm, SerieForm, ExerciceForm } from '../../shared/exercise-forms';

/**
 * Component responsible for the execution, creation, and modification of a workout session.
 * Handles both the "Template/Program" mode and the "Active Journal" execution mode.
 */
@Component({
  selector: 'app-session',
  standalone: true,
  imports: [CommonModule, FormsModule, HeaderComponent, ExerciceCardComponent, ExercisePickerComponent],
  templateUrl: './session.html',
  styleUrls: ['./session.css']
})
export class Session implements OnInit {

  /** Signal holding the title of the current routine. */
  titreRoutine = signal('Nouvelle Routine');

  /** Signal holding the current date formatted for display. */
  derniereSession = signal(new Date().toLocaleDateString('fr-FR'));

  /** The unique identifier of the routine being viewed or edited, if applicable. */
  routineId: string | null = null;

  /** Indicates whether the session is currently in a read-only state. */
  isReadonly = signal(false);

  /** Indicates whether the authenticated user is viewing someone else's session without edit rights. */
  isExternalView = signal(false);

  /** Signal holding the array of exercises configured in the current session. */
  exercices = signal<ExerciceForm[]>([]);

  /** Loading state indicator. */
  isLoading = signal(false);

  /** The username of the athlete who owns the currently loaded session. */
  targetUsername = signal<string | null>(null);

  /** Short transient feedback message shown after a save / journal action (auto-clears). */
  saveStatus = signal<string | null>(null);
  private _saveStatusTimer: ReturnType<typeof setTimeout> | null = null;

  /** "Add exercise" picker — open flag + the exos appended during the current session. */
  pickerOpen     = signal(false);
  addedExercises = signal<ExerciceForm[]>([]);

  // ── Drag & drop state for exercise reordering ──────────────────────────────
  dragFromIdx = signal(-1);
  dragOverIdx = signal(-1);

  private _from           = -1;
  private _to             = -1;
  private _touchDragging  = false;
  private _longPressTimer: any = null;
  private _touchStartX    = 0;
  private _touchStartY    = 0;

  /**
   * Initializes a new instance of the Session component.
   *
   * @param router      Angular router for navigation.
   * @param route       ActivatedRoute to parse query parameters.
   * @param chironApi   Service for backend API interactions.
   * @param authService Service for authentication state and user details.
   */
  constructor(
    private router: Router,
    private route: ActivatedRoute,
    private chironApi: ChironApi,
    private authService: AuthService
  ) {}

  /**
   * Lifecycle hook triggered on initialization.
   * Evaluates query parameters to determine the display mode (read-only, coach edit, etc.)
   * and loads the requested session data.
   */
  ngOnInit() {
    this.route.paramMap.subscribe(params => {
      this.routineId = params.get('id');

      this.route.queryParams.subscribe(queryParams => {
        // Session is now solely for executing a workout (from "Commencer") or browsing
        // an existing one read-only (from Journal / external profile). Edition/creation
        // lives in ProgrammeBuilder.
        const fromJournal = queryParams['from'] === 'journal';
        const isExternal  = queryParams['external'] === 'true';
        this.isReadonly.set(fromJournal || isExternal);
        this.isExternalView.set(isExternal);

        if (this.routineId) {
          this.chargerRoutine(this.routineId);
        } else {
          // Fallback: someone landed on `/session` without an id. Just show an empty
          // editable template so they can quickly log a one-off workout.
          this.titreRoutine.set('Nouvelle séance');
          this.exercices.set([makeEmptyExercice()]);
        }
      });
    });
  }

  /**
   * Fetches the specified workout session data from the API and populates the local form model.
   *
   * @param id The unique identifier of the session to load.
   */
  chargerRoutine(id: string) {
    const username = this.authService.getUsername();
    if (!username) return;

    this.isLoading.set(true);
    this.chironApi.getProgrammeById(username, id).subscribe({
      next: (data) => {
        this.titreRoutine.set(data.titre);

        if (data.utilisateur && data.utilisateur.username) {
            this.targetUsername.set(data.utilisateur.username);
        }

        const exosFormates: ExerciceForm[] = data.exercices.map((exo: any) => ({
          id: exo.id || generateFormId(),
          nom: exo.nom,
          definitionId: exo.exerciceDefinitionId ?? undefined,
          series: exo.series.map((serie: any) => ({
            id: serie.id || generateFormId(),
            poids: serie.poids,
            reps: serie.reps,
            degressifs: serie.degressifs ? serie.degressifs.map((deg: any) => ({
              id: deg.id || generateFormId(),
              poids: deg.poids,
              reps: deg.reps
            })) : []
          }))
        }));

        this.exercices.set(exosFormates);
        this.isLoading.set(false);
      },
      error: (err) => {
        console.error("Erreur de chargement", err);
        this.isLoading.set(false);
        alert("Impossible de charger ce programme.");
        this.router.navigate(['/programme']);
      }
    });
  }

  /**
   * Removes a specific exercise block from the session form.
   *
   * @param exoId The ID of the exercise to remove.
   */
  supprimerExercice(exoId: number | string) {
    if (this.isReadonly()) return;
    this.exercices.update(exos => exos.filter(e => e.id !== exoId));
    this.addedExercises.update(list => list.filter(e => e.id !== exoId));
  }

  // ── Library picker (bottom sheet) ─────────────────────────────────────────

  openPicker() {
    if (this.isReadonly()) return;
    this.pickerOpen.set(true);
    this.addedExercises.set([]);
  }

  closePicker() {
    this.pickerOpen.set(false);
    this.addedExercises.set([]);
  }

  /** Append an exercise to the session from a library card. */
  addExerciceFromDefinition(def: ExerciceDefinitionDto) {
    if (this.isReadonly()) return;
    const nom = def.nomFr ?? def.nomEn;
    const exo = makeEmptyExercice(nom, def.id);
    this.exercices.update(list => [...list, exo]);
    this.addedExercises.update(list => [...list, exo]);
  }

  /** Append a blank custom (free-text) exercise and close the picker. */
  addCustomExercice() {
    if (this.isReadonly()) return;
    this.exercices.update(list => [...list, makeEmptyExercice()]);
    this.closePicker();
  }

  /** Remove an exo added during this picker session — also drops it from the main list. */
  removeAddedFromPicker(exo: ExerciceForm) {
    this.supprimerExercice(exo.id);
  }

  // ── HTML5 Drag & Drop (desktop) for exercise reorder ─────────────────────────

  onExoDragStart(event: DragEvent, index: number, cardEl: HTMLElement) {
    if (this.isReadonly()) return;
    this._from = index;
    this.dragFromIdx.set(index);
    event.dataTransfer?.setData('text/plain', String(index));
    if (event.dataTransfer) event.dataTransfer.effectAllowed = 'move';
    event.dataTransfer?.setDragImage(cardEl, cardEl.offsetWidth / 2, 30);
  }

  onExoDragOver(event: DragEvent, index: number) {
    if (this.isReadonly() || this._from < 0) return;
    event.preventDefault();
    if (event.dataTransfer) event.dataTransfer.dropEffect = 'move';
    this._to = index;
    this.dragOverIdx.set(index);
  }

  onExoDrop(event: DragEvent, index: number) {
    if (this.isReadonly()) return;
    event.preventDefault();
    this._to = index;
    this._applyExoReorder();
  }

  onExoDragEnd() {
    this.dragFromIdx.set(-1);
    this.dragOverIdx.set(-1);
    this._from = -1;
    this._to   = -1;
  }

  // ── Touch drag (mobile long-press) ───────────────────────────────────────────

  onExoTouchStart(event: TouchEvent, index: number) {
    if (this.isReadonly()) return;
    const t = event.touches[0];
    this._touchStartX = t.clientX;
    this._touchStartY = t.clientY;

    this._longPressTimer = setTimeout(() => {
      this._touchDragging = true;
      this._from = index;
      this.dragFromIdx.set(index);
      if ('vibrate' in navigator) (navigator as any).vibrate(30);
    }, 350);
  }

  onExoTouchMove(event: TouchEvent) {
    if (this.isReadonly()) return;
    const t = event.touches[0];

    if (!this._touchDragging) {
      if (Math.abs(t.clientX - this._touchStartX) > 8 || Math.abs(t.clientY - this._touchStartY) > 8) {
        clearTimeout(this._longPressTimer);
      }
      return;
    }

    const el   = document.elementFromPoint(t.clientX, t.clientY) as HTMLElement;
    const card = el?.closest<HTMLElement>('[data-exo-idx]');
    if (card) {
      const idx = parseInt(card.getAttribute('data-exo-idx')!);
      if (!isNaN(idx)) {
        this._to = idx;
        this.dragOverIdx.set(idx);
      }
    }
  }

  onExoTouchEnd() {
    clearTimeout(this._longPressTimer);
    if (this._touchDragging) {
      this._applyExoReorder();
      this._touchDragging = false;
      this.dragFromIdx.set(-1);
      this.dragOverIdx.set(-1);
    }
  }

  private _applyExoReorder() {
    const from = this._from;
    const to   = this._to;
    this._from = -1;
    this._to   = -1;
    if (from < 0 || to < 0 || from === to) return;

    this.exercices.update(list => {
      const next = [...list];
      const [moved] = next.splice(from, 1);
      next.splice(to, 0, moved);
      return next;
    });
  }

  private flashStatus(msg: string) {
    this.saveStatus.set(msg);
    if (this._saveStatusTimer) clearTimeout(this._saveStatusTimer);
    this._saveStatusTimer = setTimeout(() => this.saveStatus.set(null), 3000);
  }

  /**
   * Persists the current session state to the backend as an executed Historical Session (isModele = true).
   * Calculates the current week number for statistical tracking.
   */
  ajouterAuJournal() {
    if (this.isReadonly()) return;
    const username = this.authService.getUsername();
    if (!username) return;

    const now = new Date();
    const start = new Date(now.getFullYear(), 0, 1);
    const diff = (now.getTime() - start.getTime()) + ((start.getTimezoneOffset() - now.getTimezoneOffset()) * 60000);
    const oneWeek = 1000 * 60 * 60 * 24 * 7;
    const currentWeekNumber = Math.floor(diff / oneWeek);

    // Always create a new historical session entry (the user can run the same programme
    // multiple times). The Builder owns updates to the underlying template.
    const dto = {
      id: null,
      titre: this.titreRoutine(),
      weekNumber: currentWeekNumber,
      startTime: new Date().toISOString(),
      isModele: true,
      exercices: this.exercices().map(exo => ({
        nom: exo.nom,
        commentaire: "",
        exerciceDefinitionId: exo.definitionId ?? null,
        series: exo.series.map(serie => ({
          poids: serie.poids != null ? Number(serie.poids) : 0,
          reps: serie.reps != null ? Number(serie.reps) : 0,
          commentaire: "",
          degressifs: serie.degressifs.map(deg => ({
            poids: deg.poids != null ? Number(deg.poids) : 0,
            reps: deg.reps != null ? Number(deg.reps) : 0
          }))
        }))
      }))
    };

    this.chironApi.sauvegarderProgramme(username, dto).subscribe({
      next: () => this.flashStatus('Séance ajoutée au journal.'),
      error: () => this.flashStatus("Erreur lors de l'enregistrement dans le journal."),
    });
  }

  /**
   * Navigates the user back to the previous context depending on their active mode.
   */
  retour() {
    if (this.isReadonly()) {
        this.router.navigate(['/profile']);
    } else {
        this.router.navigate(['/programme']);
    }
  }
}
