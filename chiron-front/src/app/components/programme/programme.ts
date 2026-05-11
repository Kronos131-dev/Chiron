import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { ChironApi } from '../../service/chiron-api';
import { AuthService } from '../../service/auth.service';

/**
 * Interface defining the structure of a UI-friendly workout routine summary.
 */
export interface Routine {
  id: string;
  titre: string;
  sousTitre: string;
  totalSeries: number;
}

/**
 * Component responsible for displaying the user's saved workout programs (templates).
 * Provides functionality to view, edit, create, or delete templates.
 */
@Component({
  selector: 'app-programme',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './programme.html'
})
export class Programme implements OnInit {

  /** Signal containing the list of formatted routines ready for UI rendering. */
  routines = signal<Routine[]>([]);

  /** Signal indicating whether the data is currently loading from the backend. */
  isLoading = signal(true);

  /**
   * Initializes a new instance of the Programme component.
   *
   * @param router      Angular router for navigation.
   * @param chironApi   Service for backend API interactions.
   * @param authService Service for authentication state and user details.
   */
  constructor(
    private router: Router,
    private chironApi: ChironApi,
    private authService: AuthService
  ) {}

  /**
   * Lifecycle hook triggered on initialization.
   * Invokes the loading of the user's programs.
   */
  ngOnInit() {
    this.chargerProgrammes();
  }

  /**
   * Fetches the user's workout templates from the backend and maps them to the local UI structure.
   */
  chargerProgrammes() {
    const username = this.authService.getUsername();
    if (!username) return;

    this.isLoading.set(true);
    this.chironApi.getProgrammes(username).subscribe({
      next: (data) => {
        const routinesFormatees = data.map(seance => {
          let total = 0;
          if (seance.exercices) {
            seance.exercices.forEach((exo: any) => {
              if (exo.series) total += exo.series.length;
            });
          }

          return {
            id: seance.id.toString(),
            titre: seance.titre,
            sousTitre: 'Séance',
            totalSeries: total
          };
        });

        this.routines.set(routinesFormatees);
        this.isLoading.set(false);
      },
      error: (err) => {
        console.error("Erreur lors du chargement des programmes", err);
        this.isLoading.set(false);
      }
    });
  }

  /**
   * Navigates to the session view to execute or view a specific routine.
   *
   * @param routineId The ID of the routine.
   */
  commencerRoutine(routineId: string) {
    this.router.navigate(['/session', routineId]);
  }

  /**
   * Navigates to the session view to create a brand new routine.
   */
  ajouterRoutine() {
    this.router.navigate(['/session']);
  }

  /**
   * Navigates to the session view in edit mode for a specific routine.
   *
   * @param routineId The ID of the routine to edit.
   */
  editerRoutine(routineId: string) {
    this.router.navigate(['/session', routineId]);
  }

  /**
   * Deletes a specific workout template after prompting the user for confirmation.
   * Updates the UI optimistically upon success.
   *
   * @param routineId The ID of the routine to delete.
   */
  supprimerRoutine(routineId: string) {
    if (confirm("Es-tu sûr de vouloir supprimer ce modèle de routine ?")) {
      const username = this.authService.getUsername();
      if (!username) return;

      this.chironApi.deleteProgramme(parseInt(routineId), username).subscribe({
        next: () => {
          this.routines.update(routines => routines.filter(r => r.id !== routineId));
        },
        error: (err) => {
          console.error("Erreur lors de la suppression", err);
          alert("Impossible de supprimer cette routine.");
        }
      });
    }
  }
}
