import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ChironApi } from '../../service/chiron-api';
import { AuthService } from '../../service/auth.service';
import { Router } from '@angular/router';

/**
 * Component responsible for displaying the user's workout journal history.
 * Fetches, groups, and renders completed workout sessions.
 */
@Component({
  selector: 'app-journal',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './journal.html',
  styleUrls: ['./journal.css']
})
export class Journal implements OnInit {
  /** Signal holding the raw list of historical sessions. */
  historique = signal<any[]>([]);

  /** Signal holding the sessions grouped by week for structured rendering. */
  historiqueGrouped = signal<any[]>([]);

  /** Signal indicating whether the data is currently loading. */
  isLoading = signal(true);

  /** The username of the currently authenticated user. */
  currentUsername: string | null = null;

  /** Signal tracking which session ID is currently expanded in the accordion view. */
  expandedSeanceId = signal<number | null>(null);

  /**
   * Initializes a new instance of the Journal component.
   *
   * @param chironApi   Service for backend API interactions.
   * @param authService Service for authentication state and user details.
   * @param router      Angular Router for navigation.
   */
  constructor(
    private chironApi: ChironApi,
    private authService: AuthService,
    private router: Router
  ) {}

  /**
   * Lifecycle hook that executes upon component initialization.
   * Determines the current user and triggers the journal data fetch.
   */
  ngOnInit() {
    this.currentUsername = this.authService.getUsername();
    if (this.currentUsername) {
      this.loadJournal(this.currentUsername);
    } else {
      this.isLoading.set(false);
      console.error("Aucun utilisateur connecté !");
    }
  }

  /**
   * Fetches the historical workout sessions for the specified user from the backend.
   * Updates internal signals upon successful retrieval.
   *
   * @param username The username for which to load the journal.
   */
  loadJournal(username: string) {
    this.isLoading.set(true);

    this.chironApi.getHistorique(username).subscribe({
      next: (data) => {
        this.historique.set(data);
        this.groupHistoriqueByWeekAndDay(data);
        this.isLoading.set(false);
      },
      error: (err) => {
        console.error("Erreur lors du chargement du journal", err);
        this.isLoading.set(false);
      }
    });
  }

  /**
   * Processes raw session data and groups it sequentially by week number.
   * Updates the `historiqueGrouped` signal with the structured data.
   *
   * @param data The raw array of historical sessions.
   */
  groupHistoriqueByWeekAndDay(data: any[]) {
    const groupedByWeek = data.reduce((acc: any, seance: any) => {
      const week = seance.weekNumber;
      if (!acc[week]) {
        acc[week] = [];
      }
      acc[week].push(seance);
      return acc;
    }, {});

    const result = Object.keys(groupedByWeek)
      .sort((a, b) => parseInt(b) - parseInt(a))
      .map(week => {
        return {
          weekNumber: week,
          seances: groupedByWeek[week]
        };
      });

    this.historiqueGrouped.set(result);
  }

  /**
   * Toggles the accordion state for a specific session to show or hide its details.
   *
   * @param id The ID of the session to toggle.
   */
  toggleSeanceDetails(id: number) {
    if (this.expandedSeanceId() === id) {
      this.expandedSeanceId.set(null);
    } else {
      this.expandedSeanceId.set(id);
    }
  }

  /**
   * Deletes a specific historical session after user confirmation.
   * Stops event propagation to prevent the accordion from toggling simultaneously.
   *
   * @param seanceId The ID of the session to delete.
   * @param event    The DOM event triggered by the deletion click.
   */
  supprimerSeance(seanceId: number, event: Event) {
    event.stopPropagation();
    if (confirm("Es-tu sûr de vouloir supprimer cette entrée de ton journal ?")) {
      const username = this.authService.getUsername();
      if (!username) return;

      this.chironApi.deleteProgramme(seanceId, username).subscribe({
        next: () => {
          this.loadJournal(username);
        },
        error: (err) => {
          console.error("Erreur lors de la suppression", err);
          alert("Impossible de supprimer cette entrée.");
        }
      });
    }
  }

  /**
   * Formats a raw date string into a localized French date representation.
   *
   * @param dateString The raw date string to format.
   * @return The formatted date string.
   */
  formatDate(dateString: string): string {
    const options: Intl.DateTimeFormatOptions = {
      weekday: 'long', year: 'numeric', month: 'long', day: 'numeric'
    };
    return new Date(dateString).toLocaleDateString('fr-FR', options);
  }
}
