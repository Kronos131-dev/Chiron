import { Component, OnInit, OnDestroy, HostListener, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, ActivatedRoute } from '@angular/router';
import { ChironApi, ExerciceDefinitionDto } from '../../service/chiron-api';
import { AuthService } from '../../service/auth.service';
import { ActiveSessionService } from '../../service/active-session.service';
import { HeaderComponent } from '../shared/header/header';
import { ExerciceCardComponent } from '../shared/exercice-card/exercice-card';
import { ExercisePickerComponent } from '../shared/exercise-picker/exercise-picker';
import { TranslatePipe } from '../../service/translate.pipe';
import { I18nService } from '../../service/i18n.service';
import { nowAsLocalDateTime, getIsoWeekNumber } from '../../util/local-date-time';
import {
  ExerciceForm,
  generateFormId,
  makeEmptyExercice,
  seriesFromDto,
} from '../../shared/exercise-forms';
import { ReconnaissanceVocale, creerReconnaissance } from '../../service/reconnaissance-vocale';
import { corrigerVocabulaire } from '../../util/vocabulaire-vocal';
import { creerVoix, Voix } from '../../util/voix';

// Re-export so existing imports (e.g. tests) keep working until they're migrated.
export type { DegressifForm, SerieForm, ExerciceForm } from '../../shared/exercise-forms';

/**
 * Component responsible for the execution, creation, and modification of a workout session.
 * Handles both the "Template/Program" mode and the "Active Journal" execution mode.
 */
@Component({
  selector: 'app-session',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    HeaderComponent,
    ExerciceCardComponent,
    ExercisePickerComponent,
    TranslatePipe,
  ],
  templateUrl: './session.html',
  styleUrls: ['./session.css'],
})
export class Session implements OnInit, OnDestroy {
  /** Signal holding the title of the current routine. */
  titreRoutine = signal('');

  /** Signal holding the current date formatted for display. */
  derniereSession = signal('');

  /** The unique identifier of the routine being viewed or edited, if applicable. */
  routineId: string | null = null;

  /** Indicates whether the session is currently in a read-only state. */
  isReadonly = signal(false);

  /** Indicates whether the authenticated user is viewing someone else's session without edit rights. */
  isExternalView = signal(false);

  /** Signal holding the array of exercises configured in the current session. */
  exercices = signal<ExerciceForm[]>([]);

  /** Optional free-text note for the whole session (ressenti, condition…). */
  noteSeance = signal('');

  isInteractive = signal(false);
  isRecording = signal(false);
  activeConversationId = signal<number | null>(null);
  private recognition: ReconnaissanceVocale;
  private voix: Voix | null = null;

  /** Loading state indicator. */
  isLoading = signal(false);

  /** Wall-clock tick (ms), refreshed every second to drive the live session timer. */
  private readonly now = signal(Date.now());
  private _timer: ReturnType<typeof setInterval> | null = null;

  /**
   * Live elapsed time since the session was started ("Commencer"), formatted
   * "HH:MM" (hours:minutes). `null` when there is no active session to time
   * (read-only view, or session not started) — the banner stays hidden.
   */
  readonly elapsed = computed(() => {
    const started = this.activeSession.startedAt();
    if (!started || this.isReadonly()) return null;
    const ms = this.now() - new Date(started).getTime();
    if (ms < 0) return null;
    const totalMin = Math.floor(ms / 60000);
    const hh = Math.floor(totalMin / 60)
      .toString()
      .padStart(2, '0');
    const mm = (totalMin % 60).toString().padStart(2, '0');
    return `${hh}:${mm}`;
  });

  /** The username of the athlete who owns the currently loaded session. */
  targetUsername = signal<string | null>(null);

  /** Short transient feedback message shown after a save / journal action (auto-clears). */
  saveStatus = signal<string | null>(null);
  private _saveStatusTimer: ReturnType<typeof setTimeout> | null = null;

  /** "Add exercise" picker — open flag + the exos appended during the current session. */
  pickerOpen = signal(false);
  addedExercises = signal<ExerciceForm[]>([]);

  // ── Drag & drop state for exercise reordering ──────────────────────────────
  dragFromIdx = signal(-1);
  dragOverIdx = signal(-1);

  private _from = -1;
  private _to = -1;
  private _touchDragging = false;
  private _longPressTimer: any = null;
  private _touchStartX = 0;
  private _touchStartY = 0;

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
    private authService: AuthService,
    private activeSession: ActiveSessionService,
    private i18n: I18nService,
  ) {
    this.recognition = creerReconnaissance();
  }

  /**
   * Lifecycle hook triggered on initialization.
   * Evaluates query parameters to determine the display mode (read-only, coach edit, etc.)
   * and loads the requested session data.
   */
  ngOnInit() {
    this.derniereSession.set(
      new Date().toLocaleDateString(this.i18n.lang() === 'en' ? 'en-US' : 'fr-FR'),
    );
    // Tick once a second to keep the live session timer up to date.
    this._timer = setInterval(() => this.now.set(Date.now()), 1000);

    this.route.paramMap.subscribe((params) => {
      this.routineId = params.get('id');

      this.route.queryParams.subscribe((queryParams) => {
        // Session is now solely for executing a workout (from "Commencer") or browsing
        // an existing one read-only (from Journal / external profile). Edition/creation
        // lives in ProgrammeBuilder.
        const fromJournal = queryParams['from'] === 'journal';
        const isExternal = queryParams['external'] === 'true';
        const interactive = queryParams['interactive'] === 'true';
        this.isReadonly.set(fromJournal || isExternal);
        this.isExternalView.set(isExternal);
        this.isInteractive.set(interactive);

        if (this.routineId) {
          // Execution mode: if this exact routine is already in progress, rehydrate
          // the shared in-memory state instead of refetching — keeps every rep /
          // charge entered so far after navigating away and back.
          if (!this.isReadonly() && this.activeSession.isActiveFor(this.routineId)) {
            this.titreRoutine = this.activeSession.titre;
            this.exercices = this.activeSession.exercices;
          } else {
            this.chargerRoutine(this.routineId);
          }
        } else {
          // Fallback: someone landed on `/session` without an id. Just show an empty
          // editable template so they can quickly log a one-off workout.
          this.titreRoutine.set(this.i18n.t('session.newSession'));
          this.exercices.set([makeEmptyExercice()]);
        }
      });
    });
  }

  /**
   * Lifecycle hook triggered when the component is destroyed (e.g. navigating to
   * another page). Persists the active session so its progress is durable.
   */
  ngOnDestroy() {
    if (this._timer) clearInterval(this._timer);
    this.persistActiveSession();
  }

  /**
   * Snapshots the active session to localStorage when the app is hidden or
   * backgrounded — covers the Capacitor Android app being swiped away.
   */
  @HostListener('window:pagehide')
  @HostListener('document:visibilitychange')
  persistActiveSession() {
    if (!this.isReadonly() && this.routineId && this.activeSession.isActiveFor(this.routineId)) {
      this.activeSession.snapshot();
    }
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

        this.noteSeance.set(data.note ?? '');

        const exosFormates: ExerciceForm[] = data.exercices.map((exo: any) => ({
          id: exo.id || generateFormId(),
          nom: exo.nom,
          definitionId: exo.exerciceDefinitionId ?? undefined,
          cardioType: exo.cardioType ?? null,
          wodType: exo.wodType ?? null,
          commentaire: exo.commentaire ?? '',
          unilateral: exo.unilateral ?? false,
          series: exo.series.map((serie: any) => ({
            id: serie.id || generateFormId(),
            poids: serie.poids,
            reps: serie.reps,
            dureeMin: serie.dureeMin ?? null,
            distanceM: serie.distanceM ?? null,
            allureKmh: serie.allureKmh ?? null,
            pentePct: serie.pentePct ?? null,
            calories: serie.calories ?? null,
            courseTraceId: serie.courseTraceId ?? null,
            degressifs: serie.degressifs
              ? serie.degressifs.map((deg: any) => ({
                  id: deg.id || generateFormId(),
                  poids: deg.poids,
                  reps: deg.reps,
                }))
              : [],
          })),
        }));

        this.exercices.set(exosFormates);

        // In execution mode, hand the freshly loaded session to the shared service
        // and bind the component to its signals so the state survives navigation.
        if (!this.isReadonly() && this.routineId) {
          this.activeSession.start(this.routineId, data.titre, exosFormates);
          this.titreRoutine = this.activeSession.titre;
          this.exercices = this.activeSession.exercices;
        }

        this.isLoading.set(false);
      },
      error: (err) => {
        console.error('Erreur de chargement', err);
        this.isLoading.set(false);
        alert(this.i18n.t('session.loadError'));
        this.router.navigate(['/programme']);
      },
    });
  }

  /**
   * Removes a specific exercise block from the session form.
   *
   * @param exoId The ID of the exercise to remove.
   */
  supprimerExercice(exoId: number | string) {
    if (this.isReadonly()) return;
    this.exercices.update((exos) => exos.filter((e) => e.id !== exoId));
    this.addedExercises.update((list) => list.filter((e) => e.id !== exoId));
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
    const nom = this.i18n.pick(def.nomFr, def.nomEn);
    const exo = makeEmptyExercice(
      nom,
      def.id,
      def.cardioType as ExerciceForm['cardioType'],
      def.wodType as ExerciceForm['wodType'],
    );
    this.exercices.update((list) => [...list, exo]);
    this.addedExercises.update((list) => [...list, exo]);

    if (def.id) {
      const username = this.authService.getUsername();
      if (username) {
        this.chironApi.getLastPerformance(username, def.id).subscribe({
          next: (lastExo) => {
            this.exercices.update((list) => {
              const idx = list.findIndex((e) => e.id === exo.id);
              if (idx >= 0 && lastExo.series) {
                list[idx] = { ...list[idx], series: seriesFromDto(lastExo.series) };
              }
              return list;
            });
          },
          error: () => {},
        });
      }
    }
  }

  demarrerWod(exo: ExerciceForm) {
    if (this.isReadonly() || !this.routineId) return;
    this.persistActiveSession();
    this.router.navigate(['/session', this.routineId, 'wod', exo.id]);
  }

  demarrerCourse(exo: ExerciceForm) {
    if (this.isReadonly() || !this.routineId) return;
    this.persistActiveSession();
    this.router.navigate(['/session', this.routineId, 'course', exo.id]);
  }

  /** Append a blank custom (free-text) exercise and close the picker. */
  addCustomExercice() {
    if (this.isReadonly()) return;
    this.exercices.update((list) => [...list, makeEmptyExercice()]);
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
    this._to = -1;
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
      if (
        Math.abs(t.clientX - this._touchStartX) > 8 ||
        Math.abs(t.clientY - this._touchStartY) > 8
      ) {
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
      this._applyExoReorder();
      this._touchDragging = false;
      this.dragFromIdx.set(-1);
      this.dragOverIdx.set(-1);
    }
  }

  private _applyExoReorder() {
    const from = this._from;
    const to = this._to;
    this._from = -1;
    this._to = -1;
    if (from < 0 || to < 0 || from === to) return;

    this.exercices.update((list) => {
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
   * Persists the current session state to the backend as an executed Historical Session (historique = true).
   * Calculates the current week number for statistical tracking.
   * Additionally syncs the source template programme (if the session was started from one)
   * so the next execution shows the latest reps / weights as presets.
   */
  ajouterAuJournal() {
    if (this.isReadonly()) return;
    const username = this.authService.getUsername();
    if (!username) return;

    const currentWeekNumber = getIsoWeekNumber(new Date());

    const exercicesPayload = this.exercices().map((exo) => ({
      nom: exo.nom,
      commentaire: exo.commentaire ?? '',
      unilateral: exo.unilateral ?? false,
      exerciceDefinitionId: exo.definitionId ?? null,
      series: exo.series.map((serie) => ({
        poids: serie.poids != null ? Number(serie.poids) : 0,
        reps: serie.reps != null ? Number(serie.reps) : 0,
        commentaire: '',
        dureeMin: serie.dureeMin != null ? Number(serie.dureeMin) : null,
        distanceM: serie.distanceM != null ? Number(serie.distanceM) : null,
        allureKmh: serie.allureKmh != null ? Number(serie.allureKmh) : null,
        pentePct: serie.pentePct != null ? Number(serie.pentePct) : null,
        courseTraceId: serie.courseTraceId ?? null,
        degressifs: serie.degressifs.map((deg) => ({
          poids: deg.poids != null ? Number(deg.poids) : 0,
          reps: deg.reps != null ? Number(deg.reps) : 0,
        })),
      })),
    }));

    // WHY: ce même payload part deux fois — une fois dans le journal, une fois pour remettre à
    // jour le modèle de programme. Une trace GPS appartient à la sortie du jour, pas au modèle :
    // l'y laisser rattacherait la course d'aujourd'hui à toutes celles de demain.
    const exercicesModele = exercicesPayload.map((exo) => ({
      ...exo,
      series: exo.series.map(({ courseTraceId, ...serie }) => serie),
    }));

    const journalDto = {
      id: this.isInteractive() && this.routineId ? parseInt(this.routineId, 10) : null,
      titre: this.titreRoutine(),
      note: this.noteSeance() || null,
      weekNumber: currentWeekNumber,
      startTime: this.activeSession.startedAt() ?? nowAsLocalDateTime(),
      endTime: nowAsLocalDateTime(),
      historique: true,
      exercices: exercicesPayload,
    };

    this.chironApi.sauvegarderProgramme(username, journalDto).subscribe({
      next: () => {
        this.flashStatus(this.i18n.t('session.addedToJournal'));

        // The session is now logged: detach from the shared "in progress" state
        // (a fresh start is required next time) while keeping the current view
        // intact by re-creating local signals holding the same values.
        if (this.routineId && !this.isReadonly()) {
          const exos = this.exercices();
          const titre = this.titreRoutine();
          this.activeSession.clear();
          this.exercices = signal(exos);
          this.titreRoutine = signal(titre);
        }

        if (!this.isInteractive() && this.routineId && !this.isReadonly()) {
          const templateDto = {
            id: parseInt(this.routineId, 10),
            titre: this.titreRoutine(),
            historique: false,
            exercices: exercicesModele,
          };
          this.chironApi.sauvegarderProgramme(username, templateDto).subscribe({
            error: () => this.flashStatus(this.i18n.t('session.modelUpdateFailed')),
          });
        }
      },
      error: () => this.flashStatus(this.i18n.t('session.saveError')),
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

  toggleRecording() {
    if (!this.recognition.disponible()) {
      alert(this.i18n.t('session.noMic'));
      return;
    }

    if (this.isRecording()) {
      this.recognition.arreter();
      this.isRecording.set(false);
      return;
    }

    this.isRecording.set(true);
    this.recognition.demarrer(this.i18n.lang() === 'en' ? 'en-US' : 'fr-FR', {
      final: (texte) => {
        this.isRecording.set(false);
        const corrected = corrigerVocabulaire(texte);
        this.sendVoiceCommand(corrected);
      },
      erreur: (raison) => {
        this.isRecording.set(false);
        this.flashStatus(this.i18n.t('session.micError', { error: raison }));
      },
    });
  }

  private sendVoiceCommand(message: string) {
    if (!message.trim() || !this.authService.getUsername()) return;

    this.chironApi
      .sendMessage(message, this.activeConversationId() || null, this.i18n.lang())
      .subscribe({
        next: (response) => {
          if (response?.reply) {
            this.flashStatus(response.reply);
            if (!this.voix) {
              this.voix = creerVoix(this.i18n.lang() === 'en' ? 'en-US' : 'fr-FR');
            }
            this.voix?.parler(response.reply);
          }
          if (response?.conversationId) {
            this.activeConversationId.set(response.conversationId);
          }
          this.refreshExercicesFromServer();
        },
        error: () => {
          this.flashStatus(this.i18n.t('session.coachError'));
        },
      });
  }

  private refreshExercicesFromServer() {
    if (!this.routineId) return;
    const username = this.authService.getUsername();
    if (!username) return;

    this.chironApi.getProgrammeById(username, this.routineId).subscribe({
      next: (data) => {
        const exosFormatees: ExerciceForm[] = data.exercices.map((exo: any) => ({
          id: exo.id || generateFormId(),
          nom: exo.nom,
          definitionId: exo.exerciceDefinitionId ?? undefined,
          cardioType: exo.cardioType ?? null,
          wodType: exo.wodType ?? null,
          commentaire: exo.commentaire ?? '',
          unilateral: exo.unilateral ?? false,
          series: exo.series.map((serie: any) => ({
            id: serie.id || generateFormId(),
            poids: serie.poids,
            reps: serie.reps,
            dureeMin: serie.dureeMin ?? null,
            distanceM: serie.distanceM ?? null,
            allureKmh: serie.allureKmh ?? null,
            pentePct: serie.pentePct ?? null,
            calories: serie.calories ?? null,
            courseTraceId: serie.courseTraceId ?? null,
            degressifs: serie.degressifs
              ? serie.degressifs.map((deg: any) => ({
                  id: deg.id || generateFormId(),
                  poids: deg.poids,
                  reps: deg.reps,
                }))
              : [],
          })),
        }));
        this.exercices.set(exosFormatees);
      },
      error: () => {},
    });
  }
}
