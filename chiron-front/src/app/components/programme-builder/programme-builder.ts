import { Component, OnInit, signal, computed } from '@angular/core';
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
  makeEmptyExercice,
  generateFormId,
} from '../../shared/exercise-forms';

/**
 * Standalone editor for a workout programme (template, i.e. isModele=false).
 *
 * Routes:
 *   /programme/new                  → create a new programme
 *   /programme/:id/edit             → edit an existing programme
 *   /programme/new?asUser=alice     → coach creates a programme for athlete `alice`
 *   /programme/:id/edit?asUser=alice → coach edits athlete `alice`'s programme
 *
 * The "execution" flow stays in the Session component (started via `/session/:id`
 * when the user clicks "Commencer" in the programme list).
 */
@Component({
  selector: 'app-programme-builder',
  standalone: true,
  imports: [CommonModule, FormsModule, HeaderComponent, ExerciceCardComponent, ExercisePickerComponent],
  templateUrl: './programme-builder.html',
  styleUrls: ['./programme-builder.css'],
})
export class ProgrammeBuilder implements OnInit {

  titre        = signal('Nouveau programme');
  exercices    = signal<ExerciceForm[]>([]);
  isLoading    = signal(false);
  isSaving     = signal(false);
  saveStatus   = signal<string | null>(null);
  private _saveStatusTimer: ReturnType<typeof setTimeout> | null = null;

  /** ID of the programme being edited (null for a brand-new one). */
  programmeId: string | null = null;

  /**
   * If set, this is the username of the athlete being edited on behalf of (coach mode).
   * Comes from the `?asUser=alice` query param. When null we operate on the logged-in user.
   */
  targetUsername = signal<string | null>(null);

  /** Whether the logged-in user is editing on behalf of someone else (coach flow). */
  isCoachMode = computed(() => {
    const target = this.targetUsername();
    return target !== null && target !== this.authService.getUsername();
  });

  /** "Add exercise" picker — open flag + the exos appended during the current session. */
  pickerOpen      = signal(false);
  addedExercises  = signal<ExerciceForm[]>([]);

  // ── Drag & drop state for the programme's exercise list ─────────────────────
  dragFromIdx = signal(-1);
  dragOverIdx = signal(-1);
  private _from           = -1;
  private _to             = -1;
  private _touchDragging  = false;
  private _longPressTimer: any = null;
  private _touchStartX    = 0;
  private _touchStartY    = 0;

  constructor(
    private router:      Router,
    private route:       ActivatedRoute,
    private chironApi:   ChironApi,
    private authService: AuthService,
  ) {}

  ngOnInit() {
    this.route.paramMap.subscribe(params => {
      this.programmeId = params.get('id');
      this.route.queryParamMap.subscribe(qp => {
        const asUser = qp.get('asUser');
        this.targetUsername.set(asUser);

        if (this.programmeId) {
          this.loadProgramme(this.programmeId);
        } else {
          this.titre.set('Nouveau programme');
          this.exercices.set([]);
        }
      });
    });
  }

  private loadProgramme(id: string) {
    const requester = this.authService.getUsername();
    if (!requester) return;

    this.isLoading.set(true);
    this.chironApi.getProgrammeById(requester, id).subscribe({
      next: (data) => {
        this.titre.set(data.titre);
        if (data.utilisateur?.username && !this.targetUsername()) {
          this.targetUsername.set(data.utilisateur.username);
        }

        const exos: ExerciceForm[] = (data.exercices ?? []).map((exo: any) => ({
          id: exo.id ?? generateFormId(),
          nom: exo.nom,
          definitionId: exo.exerciceDefinitionId ?? undefined,
          series: (exo.series ?? []).map((serie: any) => ({
            id: serie.id ?? generateFormId(),
            poids: serie.poids,
            reps: serie.reps,
            degressifs: (serie.degressifs ?? []).map((deg: any) => ({
              id: deg.id ?? generateFormId(),
              poids: deg.poids,
              reps: deg.reps,
            })),
          })),
        }));
        this.exercices.set(exos);
        this.isLoading.set(false);
      },
      error: () => {
        this.isLoading.set(false);
        alert('Impossible de charger ce programme.');
        this.router.navigate(['/programme']);
      },
    });
  }

  // ── Exercise add / remove ──────────────────────────────────────────────────

  /** Append an exercise to the programme — from a library card OR as a blank custom one. */
  addExerciceFromDefinition(def: ExerciceDefinitionDto) {
    const nom = def.nomFr ?? def.nomEn;
    const exo = makeEmptyExercice(nom, def.id);
    this.exercices.update(list => [...list, exo]);
    this.addedExercises.update(list => [...list, exo]);
  }

  addCustomExercice() {
    const exo = makeEmptyExercice();
    this.exercices.update(list => [...list, exo]);
    this.closePicker();
  }

  removeExercice(exoId: number | string) {
    this.exercices.update(list => list.filter(e => e.id !== exoId));
    this.addedExercises.update(list => list.filter(e => e.id !== exoId));
  }

  // ── Library picker (bottom sheet) ──────────────────────────────────────────

  openPicker() {
    this.pickerOpen.set(true);
    this.addedExercises.set([]);
  }

  closePicker() {
    this.pickerOpen.set(false);
    this.addedExercises.set([]);
  }

  /** Remove an exo that was just added during the current picker session. */
  removeAddedFromPicker(exo: ExerciceForm) {
    this.removeExercice(exo.id);
  }

  // ── Save (create or update) ────────────────────────────────────────────────

  save() {
    const requester = this.authService.getUsername();
    if (!requester) return;
    // In coach mode, the requester is the coach but the programme is owned by `targetUser`.
    // For UPDATE the backend infers the owner from the existing programme (it just checks
    // requester is a coach of that owner). For CREATE we must pass `forUsername` explicitly.
    const forUsername = this.targetUsername() ?? undefined;

    const dto = {
      id: this.programmeId ? parseInt(this.programmeId) : null,
      titre: this.titre(),
      weekNumber: 0,
      isModele: false,
      exercices: this.exercices().map(exo => ({
        nom: exo.nom,
        commentaire: '',
        exerciceDefinitionId: exo.definitionId ?? null,
        series: exo.series.map(serie => ({
          poids: serie.poids != null ? Number(serie.poids) : 0,
          reps:  serie.reps  != null ? Number(serie.reps)  : 0,
          commentaire: '',
          degressifs: serie.degressifs.map(deg => ({
            poids: deg.poids != null ? Number(deg.poids) : 0,
            reps:  deg.reps  != null ? Number(deg.reps)  : 0,
          })),
        })),
      })),
    };

    this.isSaving.set(true);
    this.chironApi.sauvegarderProgramme(requester, dto, forUsername).subscribe({
      next: (response) => {
        const match = typeof response === 'string' ? response.match(/ID:\s*(\d+)/) : null;
        if (match && !this.programmeId) {
          this.programmeId = match[1];
        }
        this.isSaving.set(false);
        this.flashStatus('Programme sauvegardé.');
      },
      error: () => {
        this.isSaving.set(false);
        this.flashStatus('Erreur lors de la sauvegarde.');
      },
    });
  }

  private flashStatus(msg: string) {
    this.saveStatus.set(msg);
    if (this._saveStatusTimer) clearTimeout(this._saveStatusTimer);
    this._saveStatusTimer = setTimeout(() => this.saveStatus.set(null), 3000);
  }

  retour() {
    if (this.isCoachMode()) {
      this.router.navigate(['/profile', this.targetUsername()]);
    } else {
      this.router.navigate(['/programme']);
    }
  }

  // ── Drag & drop ────────────────────────────────────────────────────────────

  onExoDragStart(event: DragEvent, index: number, cardEl: HTMLElement) {
    this._from = index;
    this.dragFromIdx.set(index);
    event.dataTransfer?.setData('text/plain', String(index));
    if (event.dataTransfer) event.dataTransfer.effectAllowed = 'move';
    event.dataTransfer?.setDragImage(cardEl, cardEl.offsetWidth / 2, 30);
  }

  onExoDragOver(event: DragEvent, index: number) {
    if (this._from < 0) return;
    event.preventDefault();
    if (event.dataTransfer) event.dataTransfer.dropEffect = 'move';
    this._to = index;
    this.dragOverIdx.set(index);
  }

  onExoDrop(event: DragEvent, index: number) {
    event.preventDefault();
    this._to = index;
    this.applyReorder();
  }

  onExoDragEnd() {
    this.dragFromIdx.set(-1);
    this.dragOverIdx.set(-1);
    this._from = -1;
    this._to   = -1;
  }

  onExoTouchStart(event: TouchEvent, index: number) {
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
    const t = event.touches[0];
    if (!this._touchDragging) {
      if (Math.abs(t.clientX - this._touchStartX) > 8 || Math.abs(t.clientY - this._touchStartY) > 8) {
        clearTimeout(this._longPressTimer);
      }
      return;
    }
    const el = document.elementFromPoint(t.clientX, t.clientY) as HTMLElement;
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
      this.applyReorder();
      this._touchDragging = false;
      this.dragFromIdx.set(-1);
      this.dragOverIdx.set(-1);
    }
  }

  private applyReorder() {
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
}
