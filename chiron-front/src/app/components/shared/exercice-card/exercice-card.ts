import { Component, ElementRef, EventEmitter, HostListener, Input, OnInit, Output, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { ChironApi, ExerciceDefinitionDto } from '../../../service/chiron-api';
import { I18nService } from '../../../service/i18n.service';
import { TranslatePipe, LocalizePipe } from '../../../service/translate.pipe';
import {
  ExerciceForm,
  BlockType,
  WodType,
  makeEmptySerie,
  makeEmptyCardioSerie,
  makeEmptyDegressif,
  makeSerieFor,
} from '../../../shared/exercise-forms';
import { WodSpec, wodSpec } from '../../../shared/wod-specs';

/**
 * Inline editable card for one exercise in a programme/session form.
 *
 * Owns: serie/degressif add-remove, name autocomplete against the exercise definition DB.
 * Delegates to parent: removal of the whole card, drag-and-drop coordination, persistence.
 */
@Component({
  selector: 'app-exercice-card',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslatePipe, LocalizePipe],
  templateUrl: './exercice-card.html',
  styleUrls: ['./exercice-card.css'],
})
export class ExerciceCardComponent implements OnInit {

  @Input({ required: true }) exercice!: ExerciceForm;
  @Input() readonly = false;
  @Input() index = 0;
  @Input() isDragging = false;
  @Input() isDropTarget = false;
  /** Indique si l'exo est déjà dans un superset (cache le bouton "Grouper"). */
  @Input() inBlock = false;
  /** Indique s'il existe un exo suivant — désactive le bouton "Grouper" sur le dernier. */
  @Input() hasNext = false;
  @Input() canStartWod = false;

  @Output() remove = new EventEmitter<void>();
  @Output() startWod = new EventEmitter<void>();
  @Output() groupWithNext = new EventEmitter<BlockType>();
  @Output() exoDragStart = new EventEmitter<{ event: DragEvent; cardEl: HTMLElement }>();
  @Output() exoDragOver  = new EventEmitter<DragEvent>();
  @Output() exoDrop      = new EventEmitter<DragEvent>();
  @Output() exoDragEnd   = new EventEmitter<void>();
  @Output() exoTouchStart = new EventEmitter<TouchEvent>();
  @Output() exoTouchMove  = new EventEmitter<TouchEvent>();
  @Output() exoTouchEnd   = new EventEmitter<void>();

  suggestions  = signal<ExerciceDefinitionDto[]>([]);
  showSuggestions = signal(false);
  definition   = signal<ExerciceDefinitionDto | null>(null);
  groupMenuOpen = signal(false);
  private debounceTimer: ReturnType<typeof setTimeout> | null = null;

  constructor(private chironApi: ChironApi, private router: Router, private host: ElementRef<HTMLElement>, public i18n: I18nService) {}

  /** Vrai si l'exercice est un cardio (saisie durée/vitesse/pente/distance au lieu de poids/reps). */
  isCardio(): boolean {
    return !!this.exercice.cardioType;
  }

  isWod(): boolean {
    return this.spec() !== null;
  }

  spec(): WodSpec | null {
    return wodSpec(this.exercice.wodType);
  }

  scoreWod(): number | null {
    return this.exercice.series[0]?.reps ?? null;
  }

  /** Bascule l'exercice en unilatéral / bilatéral (tonnage ×2 + info pour Chiron). */
  toggleUnilateral(): void {
    if (this.readonly) return;
    this.exercice.unilateral = !this.exercice.unilateral;
  }

  /** Total des calories brûlées de l'exercice (somme des séries), ou null si aucune. */
  caloriesTotales(): number | null {
    const total = this.exercice.series.reduce((sum, s) => sum + (s.calories ?? 0), 0);
    return total > 0 ? Math.round(total) : null;
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent) {
    if (!this.groupMenuOpen()) return;
    const target = event.target as HTMLElement;
    const menuRoot = this.host.nativeElement.querySelector('.group-menu-root');
    if (menuRoot && !menuRoot.contains(target)) {
      this.groupMenuOpen.set(false);
    }
  }

  toggleGroupMenu(event: MouseEvent) {
    event.stopPropagation();
    this.groupMenuOpen.update(v => !v);
  }

  chooseGroupType(type: BlockType) {
    this.groupMenuOpen.set(false);
    this.groupWithNext.emit(type);
  }

  ngOnInit() {
    if (this.exercice.definitionId) {
      this.chironApi.getExerciceById(this.exercice.definitionId).subscribe({
        next: (def) => this.definition.set(def),
        error: () => {},
      });
    }
  }

  voirFiche() {
    if (this.exercice.definitionId) {
      this.router.navigate(['/exercice', this.exercice.definitionId]);
    }
  }

  onNomInput(event: Event) {
    const query = (event.target as HTMLInputElement).value;
    this.exercice.nom = query;
    this.exercice.definitionId = undefined;
    this.exercice.cardioType = null;
    this.exercice.wodType = null;

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
    this.exercice.nom = this.i18n.pick(def.nomFr, def.nomEn);
    this.exercice.definitionId = def.id;
    this.exercice.cardioType = (def.cardioType as ExerciceForm['cardioType']) ?? null;
    this.exercice.wodType = (def.wodType as WodType) ?? null;
    if (this.exercice.wodType) {
      this.exercice.series = [makeSerieFor(null, this.exercice.wodType)];
    } else if (this.exercice.cardioType && this.exercice.series.length > 0 && this.exercice.series[0].dureeMin === undefined) {
      this.exercice.series = [makeEmptyCardioSerie()];
    }
    this.suggestions.set([]);
    this.showSuggestions.set(false);
  }

  closeSuggestions() {
    // setTimeout so a click on a suggestion lands before the dropdown disappears.
    setTimeout(() => this.showSuggestions.set(false), 200);
  }

  ajouterSerie() {
    if (this.readonly) return;
    const nouvelle = this.isCardio() ? makeEmptyCardioSerie() : makeEmptySerie();
    this.exercice.series = [...this.exercice.series, nouvelle];
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
