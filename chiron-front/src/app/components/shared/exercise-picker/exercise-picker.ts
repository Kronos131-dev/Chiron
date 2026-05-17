import { Component, EventEmitter, Input, Output, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ChironApi, ExerciceDefinitionDto } from '../../../service/chiron-api';
import { ExerciceForm } from '../../../shared/exercise-forms';
import {
  MUSCLES, EQUIPEMENTS, DIFFICULTES,
  muscleLabel, equipementLabel, difficulteLabel, difficulteClass,
} from '../../../shared/exercise-filters';

/**
 * Full-screen "Add exercise" sheet shared by ProgrammeBuilder (template edit) and
 * Session (workout execution). Owns the search/filter UI; the parent owns the
 * actual exercise list and decides what to do with picks.
 *
 * `addedExercises` is the list of exos the parent has appended *during this picker
 * session* — rendered in the "Sélection" panel so the user can review and undo.
 */
@Component({
  selector: 'app-exercise-picker',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './exercise-picker.html',
  styleUrls: ['./exercise-picker.css'],
})
export class ExercisePickerComponent {

  @Input({ required: true }) open = false;
  @Input() addedExercises: ExerciceForm[] = [];

  @Output() closeRequest     = new EventEmitter<void>();
  @Output() addFromDefinition = new EventEmitter<ExerciceDefinitionDto>();
  @Output() addCustom        = new EventEmitter<void>();
  @Output() removeAdded      = new EventEmitter<ExerciceForm>();

  query       = signal('');
  muscle      = signal<string | null>(null);
  equipement  = signal<string | null>(null);
  difficulte  = signal<string | null>(null);
  results     = signal<ExerciceDefinitionDto[]>([]);
  loading     = signal(false);
  hasSearched = signal(false);
  private debounce: ReturnType<typeof setTimeout> | null = null;

  readonly muscles     = MUSCLES;
  readonly equipements = EQUIPEMENTS;
  readonly difficultes = DIFFICULTES;
  muscleLabel     = muscleLabel;
  equipementLabel = equipementLabel;
  difficulteLabel = difficulteLabel;
  difficulteClass = difficulteClass;

  constructor(private chironApi: ChironApi) {}

  close() {
    this.query.set('');
    this.muscle.set(null);
    this.equipement.set(null);
    this.difficulte.set(null);
    this.results.set([]);
    this.hasSearched.set(false);
    this.closeRequest.emit();
  }

  onQueryInput(value: string) {
    this.query.set(value);
    if (this.debounce) clearTimeout(this.debounce);
    this.debounce = setTimeout(() => this.runQuery(), 300);
  }

  toggleMuscle(key: string) {
    this.muscle.set(this.muscle() === key ? null : key);
    this.runQuery();
  }

  toggleEquipement(key: string) {
    this.equipement.set(this.equipement() === key ? null : key);
    this.runQuery();
  }

  toggleDifficulte(key: string) {
    this.difficulte.set(this.difficulte() === key ? null : key);
    this.runQuery();
  }

  private runQuery() {
    const q = this.query().trim();
    const muscle = this.muscle();
    const equipement = this.equipement();
    const difficulte = this.difficulte();

    if (!q && !muscle && !equipement && !difficulte) {
      this.results.set([]);
      this.hasSearched.set(false);
      return;
    }

    this.loading.set(true);
    this.hasSearched.set(true);
    this.chironApi.searchExercices(q, muscle ?? undefined, equipement ?? undefined, difficulte ?? undefined).subscribe({
      next: (results) => {
        this.results.set(results);
        this.loading.set(false);
      },
      error: () => {
        this.results.set([]);
        this.loading.set(false);
      },
    });
  }
}
