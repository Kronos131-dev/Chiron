import { Component, OnInit, signal } from '@angular/core';
import { CommonModule, Location } from '@angular/common';
import { ActivatedRoute } from '@angular/router';
import { ChironApi, ExerciceDefinitionDto } from '../../service/chiron-api';
import {
  muscleLabel,
  equipementLabel,
  difficulteLabel,
  difficulteClass,
  MUSCLES,
} from '../../shared/exercise-filters';

@Component({
  selector: 'app-exercice-detail',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './exercice-detail.html',
  styleUrl: './exercice-detail.css',
})
export class ExerciceDetailComponent implements OnInit {

  exercice = signal<ExerciceDefinitionDto | null>(null);
  loading  = signal(true);
  error    = signal(false);

  muscleLabel     = muscleLabel;
  equipementLabel = equipementLabel;
  difficulteLabel = difficulteLabel;
  difficulteClass = difficulteClass;

  constructor(
    private route: ActivatedRoute,
    private chironApi: ChironApi,
    private location: Location,
  ) {}

  ngOnInit() {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    if (!id) { this.error.set(true); this.loading.set(false); return; }

    this.chironApi.getExerciceById(id).subscribe({
      next: (def) => { this.exercice.set(def); this.loading.set(false); },
      error: ()  => { this.error.set(true);   this.loading.set(false); },
    });
  }

  retour() { this.location.back(); }
}
