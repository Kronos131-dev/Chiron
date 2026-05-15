import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ChironApi, ExerciceDefinitionDto } from '../../service/chiron-api';
import { HeaderComponent } from '../shared/header/header';

interface FilterChip {
  key: string;
  label: string;
}

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

  readonly muscles: FilterChip[] = [
    { key: 'PECTORAUX', label: 'Pectoraux' },
    { key: 'DOS', label: 'Dos' },
    { key: 'EPAULES', label: 'Épaules' },
    { key: 'BICEPS', label: 'Biceps' },
    { key: 'TRICEPS', label: 'Triceps' },
    { key: 'ABDOMINAUX', label: 'Abdos' },
    { key: 'QUADRICEPS', label: 'Quadriceps' },
    { key: 'ISCHIO_JAMBIERS', label: 'Ischio-jamb.' },
    { key: 'FESSIERS', label: 'Fessiers' },
    { key: 'MOLLETS', label: 'Mollets' },
    { key: 'AVANT_BRAS', label: 'Avant-bras' },
    { key: 'TRAPEZES', label: 'Trapèzes' },
    { key: 'LOMBAIRES', label: 'Lombaires' },
  ];

  readonly equipements: FilterChip[] = [
    { key: 'POIDS_DU_CORPS', label: 'Poids du corps' },
    { key: 'HALTERES', label: 'Haltères' },
    { key: 'BARRE', label: 'Barre' },
    { key: 'MACHINE', label: 'Machine' },
    { key: 'POULIE', label: 'Poulie' },
    { key: 'KETTLEBELL', label: 'Kettlebell' },
    { key: 'ELASTIQUE', label: 'Élastique' },
  ];

  readonly difficultes: FilterChip[] = [
    { key: 'DEBUTANT', label: 'Débutant' },
    { key: 'INTERMEDIAIRE', label: 'Intermédiaire' },
    { key: 'AVANCE', label: 'Avancé' },
  ];

  constructor(private chironApi: ChironApi) {}

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

  muscleLabel(key: string | null): string {
    if (!key) return '';
    return this.muscles.find(m => m.key === key)?.label ?? key;
  }

  equipementLabel(key: string | null): string {
    if (!key) return '';
    return this.equipements.find(e => e.key === key)?.label ?? key;
  }

  difficulteLabel(key: string | null): string {
    if (!key) return '';
    return this.difficultes.find(d => d.key === key)?.label ?? key;
  }

  difficulteClass(key: string | null): string {
    switch (key) {
      case 'DEBUTANT': return 'text-emerald-400';
      case 'INTERMEDIAIRE': return 'text-amber-400';
      case 'AVANCE': return 'text-red-400';
      default: return 'text-on-surface-variant';
    }
  }
}
