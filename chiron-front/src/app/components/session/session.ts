import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, ActivatedRoute } from '@angular/router';
import { ChironApi, ExerciceDefinitionDto } from '../../service/chiron-api';
import { AuthService } from '../../service/auth.service';
import { HeaderComponent } from '../shared/header/header';

/**
 * Interface defining the structure of a drop set (degressif) within the session form.
 */
export interface DegressifForm {
  id: number | string;
  poids: number | null;
  reps: number | null;
}

/**
 * Interface defining the structure of a set (serie) within the session form.
 */
export interface SerieForm {
  id: number | string;
  poids: number | null;
  reps: number | null;
  degressifs: DegressifForm[];
}

/**
 * Interface defining the structure of an exercise within the session form.
 */
export interface ExerciceForm {
  id: number | string;
  nom: string;
  definitionId?: number;
  series: SerieForm[];
}

/**
 * Component responsible for the execution, creation, and modification of a workout session.
 * Handles both the "Template/Program" mode and the "Active Journal" execution mode.
 */
@Component({
  selector: 'app-session',
  standalone: true,
  imports: [CommonModule, FormsModule, HeaderComponent],
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

  /** Indicates whether the authenticated user is editing the session on behalf of their assigned athlete. */
  isCoachEdit = signal(false);

  /** Indicates if the currently loaded session data represents a template (model) or an executed historical session. */
  loadedSeanceIsModele = signal(false);

  /** Signal holding the array of exercises configured in the current session. */
  exercices = signal<ExerciceForm[]>([]);

  /** Loading state indicator. */
  isLoading = signal(false);

  suggestions = signal<ExerciceDefinitionDto[]>([]);
  activeSearchExoId = signal<number | string | null>(null);
  private debounceTimer: ReturnType<typeof setTimeout> | null = null;

  /** The username of the athlete who owns the currently loaded session. */
  targetUsername = signal<string | null>(null);

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
        if (queryParams['from'] === 'journal') {
          this.isReadonly.set(true);
        } else {
          this.isReadonly.set(false);
        }

        if (queryParams['external'] === 'true') {
            this.isReadonly.set(true);
            this.isExternalView.set(true);
        } else {
            this.isExternalView.set(false);
        }

        if (queryParams['coachEdit'] === 'true') {
            this.isReadonly.set(false);
            this.isCoachEdit.set(true);
        }

        if (this.routineId) {
          this.chargerRoutine(this.routineId);
        } else {
          this.titreRoutine.set('Nouvelle Routine');
          this.loadedSeanceIsModele.set(false);
          this.exercices.set([
            { id: this.generateUniqueId(), nom: '', series: [{ id: this.generateUniqueId(), poids: null, reps: null, degressifs: [] }] }
          ]);
        }
      });
    });
  }

  /**
   * Utility method to generate unique identifiers for dynamic UI form elements.
   *
   * @return A randomly generated string ID.
   */
  private generateUniqueId(): string {
     return Math.random().toString(36).substr(2, 9) + '_' + Date.now();
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
        this.loadedSeanceIsModele.set(data.modele);

        if (data.utilisateur && data.utilisateur.username) {
            this.targetUsername.set(data.utilisateur.username);
        }

        const exosFormates: ExerciceForm[] = data.exercices.map((exo: any) => ({
          id: exo.id || this.generateUniqueId(),
          nom: exo.nom,
          definitionId: exo.exerciceDefinitionId ?? undefined,
          series: exo.series.map((serie: any) => ({
            id: serie.id || this.generateUniqueId(),
            poids: serie.poids,
            reps: serie.reps,
            degressifs: serie.degressifs ? serie.degressifs.map((deg: any) => ({
              id: deg.id || this.generateUniqueId(),
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
   * Adds a new empty exercise block to the session form.
   */
  ajouterExercice() {
    if (this.isReadonly()) return;
    this.exercices.update(exos => [
      ...exos,
      { id: this.generateUniqueId(), nom: '', series: [{ id: this.generateUniqueId(), poids: null, reps: null, degressifs: [] }] }
    ]);
  }

  /**
   * Removes a specific exercise block from the session form.
   *
   * @param exoId The ID of the exercise to remove.
   */
  supprimerExercice(exoId: number | string) {
    if (this.isReadonly()) return;
    this.exercices.update(exos => exos.filter(e => e.id !== exoId));
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

  /**
   * Adds a new empty set (serie) to a specific exercise block.
   *
   * @param exoId The ID of the parent exercise.
   */
  ajouterSerie(exoId: number | string) {
    if (this.isReadonly()) return;
    this.exercices.update(exos => exos.map(exo => {
      if (exo.id === exoId) {
        return { ...exo, series: [...exo.series, { id: this.generateUniqueId(), poids: null, reps: null, degressifs: [] }] };
      }
      return exo;
    }));
  }

  /**
   * Removes a specific set (serie) from its parent exercise block.
   *
   * @param exoId   The ID of the parent exercise.
   * @param serieId The ID of the set to remove.
   */
  supprimerSerie(exoId: number | string, serieId: number | string) {
    if (this.isReadonly()) return;
    this.exercices.update(exos => exos.map(exo => {
      if (exo.id === exoId) {
        return { ...exo, series: exo.series.filter(s => s.id !== serieId) };
      }
      return exo;
    }));
  }

  /**
   * Adds a new empty drop set (degressif) to a specific serie.
   *
   * @param exoId The ID of the parent exercise.
   * @param serieId The ID of the parent serie.
   */
  ajouterDegressif(exoId: number | string, serieId: number | string) {
    if (this.isReadonly()) return;
    this.exercices.update(exos => exos.map(exo => {
      if (exo.id === exoId) {
        return {
          ...exo,
          series: exo.series.map(serie => {
            if (serie.id === serieId) {
              return { ...serie, degressifs: [...serie.degressifs, { id: this.generateUniqueId(), poids: null, reps: null }] };
            }
            return serie;
          })
        };
      }
      return exo;
    }));
  }

  /**
   * Removes a specific drop set (degressif) from its parent serie.
   *
   * @param exoId The ID of the parent exercise.
   * @param serieId The ID of the parent serie.
   * @param degressifId The ID of the drop set to remove.
   */
  supprimerDegressif(exoId: number | string, serieId: number | string, degressifId: number | string) {
    if (this.isReadonly()) return;
    this.exercices.update(exos => exos.map(exo => {
      if (exo.id === exoId) {
        return {
          ...exo,
          series: exo.series.map(serie => {
            if (serie.id === serieId) {
              return { ...serie, degressifs: serie.degressifs.filter(d => d.id !== degressifId) };
            }
            return serie;
          })
        };
      }
      return exo;
    }));
  }

  onNomInput(event: Event, exo: ExerciceForm) {
    const query = (event.target as HTMLInputElement).value;
    exo.nom = query;
    exo.definitionId = undefined;

    if (this.debounceTimer) clearTimeout(this.debounceTimer);

    if (!query || query.length < 2) {
      this.suggestions.set([]);
      this.activeSearchExoId.set(null);
      return;
    }

    this.debounceTimer = setTimeout(() => {
      this.chironApi.searchExercices(query).subscribe({
        next: (results) => {
          this.suggestions.set(results.slice(0, 20));
          this.activeSearchExoId.set(exo.id);
        },
        error: () => this.suggestions.set([])
      });
    }, 300);
  }

  selectDefinition(def: ExerciceDefinitionDto, exo: ExerciceForm) {
    exo.nom = def.nomFr ?? def.nomEn;
    exo.definitionId = def.id;
    this.suggestions.set([]);
    this.activeSearchExoId.set(null);
  }

  closeSuggestions() {
    setTimeout(() => {
      this.suggestions.set([]);
      this.activeSearchExoId.set(null);
    }, 200);
  }

  displayNom(exo: ExerciceForm): string {
    return exo.nom;
  }

  /**
   * Persists the current session state to the backend as a reusable Program Template (isModele = false).
   */
  sauvegarder() {
    if (this.isReadonly()) return;

    const username = this.authService.getUsername();
    if (!username) return;

    const forceNewModel = this.loadedSeanceIsModele() === true;
    const finalId = forceNewModel ? null : (this.routineId ? parseInt(this.routineId) : null);

    const dto = {
      id: finalId,
      titre: this.titreRoutine(),
      weekNumber: 0,
      isModele: false,
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
      next: () => {
          if (this.isCoachEdit()) {
              this.router.navigate(['/profile', this.targetUsername()]);
          } else {
              this.router.navigate(['/programme']);
          }
      },
      error: (err) => alert("Erreur lors de la sauvegarde du modèle.")
    });
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

    const forceNewHistory = this.loadedSeanceIsModele() === false || this.routineId === null;
    const finalId = forceNewHistory ? null : parseInt(this.routineId!);

    const dto = {
      id: finalId,
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
      next: () => {
        alert("Séance enregistrée dans votre journal avec succès !");
        this.router.navigate(['/journal']);
      },
      error: (err) => alert("Erreur lors de l'enregistrement dans le journal.")
    });
  }

  /**
   * Navigates the user back to the previous context depending on their active mode.
   */
  retour() {
    if (this.isReadonly() || this.isCoachEdit()) {
        const target = this.targetUsername() || this.authService.getUsername();
        if (this.isCoachEdit()) {
             this.router.navigate(['/profile', target]);
        } else {
             this.router.navigate(['/profile']);
        }
    } else {
        this.router.navigate(['/programme']);
    }
  }
}
