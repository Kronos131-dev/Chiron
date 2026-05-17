import { Component, EventEmitter, Input, Output, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ChironApi, ExerciceDefinitionDto } from '../../../service/chiron-api';
import {
  ExerciceForm,
  makeEmptySerie,
  makeEmptyDegressif,
} from '../../../shared/exercise-forms';

/**
 * Inline editable card for one exercise in a programme/session form.
 *
 * Owns: serie/degressif add-remove, name autocomplete against the exercise definition DB.
 * Delegates to parent: removal of the whole card, drag-and-drop coordination, persistence.
 */
@Component({
  selector: 'app-exercice-card',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './exercice-card.html',
  styleUrls: ['./exercice-card.css'],
})
export class ExerciceCardComponent {

  @Input({ required: true }) exercice!: ExerciceForm;
  @Input() readonly = false;
  @Input() index = 0;
  @Input() isDragging = false;
  @Input() isDropTarget = false;

  @Output() remove = new EventEmitter<void>();
  @Output() exoDragStart = new EventEmitter<{ event: DragEvent; cardEl: HTMLElement }>();
  @Output() exoDragOver  = new EventEmitter<DragEvent>();
  @Output() exoDrop      = new EventEmitter<DragEvent>();
  @Output() exoDragEnd   = new EventEmitter<void>();
  @Output() exoTouchStart = new EventEmitter<TouchEvent>();
  @Output() exoTouchMove  = new EventEmitter<TouchEvent>();
  @Output() exoTouchEnd   = new EventEmitter<void>();

  suggestions = signal<ExerciceDefinitionDto[]>([]);
  showSuggestions = signal(false);
  private debounceTimer: ReturnType<typeof setTimeout> | null = null;

  constructor(private chironApi: ChironApi) {}

  onNomInput(event: Event) {
    const query = (event.target as HTMLInputElement).value;
    this.exercice.nom = query;
    this.exercice.definitionId = undefined;

    if (this.debounceTimer) clearTimeout(this.debounceTimer);

    if (!query || query.length < 2) {
      this.suggestions.set([]);
      this.showSuggestions.set(false);
      return;
    }

    this.debounceTimer = setTimeout(() => {
      this.chironApi.searchExercices(query).subscribe({
        next: (results) => {
          this.suggestions.set(results.slice(0, 20));
          this.showSuggestions.set(true);
        },
        error: () => this.suggestions.set([]),
      });
    }, 300);
  }

  selectDefinition(def: ExerciceDefinitionDto) {
    this.exercice.nom = def.nomFr ?? def.nomEn;
    this.exercice.definitionId = def.id;
    this.suggestions.set([]);
    this.showSuggestions.set(false);
  }

  closeSuggestions() {
    // setTimeout so a click on a suggestion lands before the dropdown disappears.
    setTimeout(() => this.showSuggestions.set(false), 200);
  }

  ajouterSerie() {
    if (this.readonly) return;
    this.exercice.series = [...this.exercice.series, makeEmptySerie()];
  }

  supprimerSerie(serieId: number | string) {
    if (this.readonly) return;
    this.exercice.series = this.exercice.series.filter(s => s.id !== serieId);
  }

  ajouterDegressif(serieId: number | string) {
    if (this.readonly) return;
    const serie = this.exercice.series.find(s => s.id === serieId);
    if (!serie) return;
    serie.degressifs = [...serie.degressifs, makeEmptyDegressif()];
  }

  supprimerDegressif(serieId: number | string, degressifId: number | string) {
    if (this.readonly) return;
    const serie = this.exercice.series.find(s => s.id === serieId);
    if (!serie) return;
    serie.degressifs = serie.degressifs.filter(d => d.id !== degressifId);
  }

  // ── Drag handlers — just forward to parent; parent owns the index-based coordination ────

  onDragStart(event: DragEvent, cardEl: HTMLElement) {
    if (this.readonly) return;
    this.exoDragStart.emit({ event, cardEl });
  }
}
