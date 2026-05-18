import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { ChironApi, ExerciceDefinitionDto } from '../../service/chiron-api';
import { HeaderComponent } from '../shared/header/header';
import {
  MUSCLES,
  EQUIPEMENTS,
  DIFFICULTES,
  muscleLabel,
  equipementLabel,
  difficulteLabel,
  difficulteClass,
} from '../../shared/exercise-filters';

@Component({
  selector: 'app-bibliotheque',
  standalone: true,
  imports: [CommonModule, FormsModule, HeaderComponent],
  templateUrl: './bibliotheque.html',
  styleUrl: './bibliotheque.css'
})
export class Bibliotheque implements OnInit {

  exercices = signal<ExerciceDefinitionDto[]>([]);
  loading = signal(false);
  searchQuery = signal('');
  selectedMuscle = signal<string | null>(null);
  selectedEquipement = signal<string | null>(null);
  selectedDifficulte = signal<string | null>(null);
  selectedExercice = signal<ExerciceDefinitionDto | null>(null);
  hasSearched = signal(false);

  private debounceTimer: ReturnType<typeof setTimeout> | null = null;

  readonly muscles     = MUSCLES;
  readonly equipements = EQUIPEMENTS;
  readonly difficultes = DIFFICULTES;

  constructor(private chironApi: ChironApi, private router: Router) {}

  ngOnInit() {}

  onSearchInput(value: string) {
    this.searchQuery.set(value);
    if (this.debounceTimer) clearTimeout(this.debounceTimer);
    this.debounceTimer = setTimeout(() => this.loadExercices(), 300);
  }

  toggleMuscle(key: string) {
    this.selectedMuscle.set(this.selectedMuscle() === key ? null : key);
    this.loadExercices();
  }

  toggleEquipement(key: string) {
    this.selectedEquipement.set(this.selectedEquipement() === key ? null : key);
    this.loadExercices();
  }

  toggleDifficulte(key: string) {
    this.selectedDifficulte.set(this.selectedDifficulte() === key ? null : key);
    this.loadExercices();
  }

  loadExercices() {
    const q = this.searchQuery().trim();
    const muscle = this.selectedMuscle();
    const equipement = this.selectedEquipement();
    const difficulte = this.selectedDifficulte();

    if (!q && !muscle && !equipement && !difficulte) {
      this.exercices.set([]);
      this.hasSearched.set(false);
      return;
    }

    this.loading.set(true);
    this.hasSearched.set(true);
    this.chironApi.searchExercices(q, muscle ?? undefined, equipement ?? undefined, difficulte ?? undefined).subscribe({
      next: (results) => {
        this.exercices.set(results);
        this.loading.set(false);
      },
      error: () => {
        this.exercices.set([]);
        this.loading.set(false);
      }
    });
  }

  openDetail(exercice: ExerciceDefinitionDto) {
    this.selectedExercice.set(exercice);
  }

  closeDetail() {
    this.selectedExercice.set(null);
  }

  voirFiche() {
    const exo = this.selectedExercice();
    if (exo) this.router.navigate(['/exercice', exo.id]);
  }

  muscleLabel     = muscleLabel;
  equipementLabel = equipementLabel;
  difficulteLabel = difficulteLabel;
  difficulteClass = difficulteClass;
}
