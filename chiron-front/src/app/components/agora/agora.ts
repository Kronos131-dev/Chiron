import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { ChironApi } from '../../service/chiron-api';
import { AuthService } from '../../service/auth.service';
import { HeaderComponent } from '../shared/header/header';

/**
 * Component representing the "Agora" view, which is the main hub
 * for discovering and searching for other participants (athletes).
 */
@Component({
  selector: 'app-agora',
  standalone: true,
  imports: [CommonModule, FormsModule, HeaderComponent],
  templateUrl: './agora.html'
})
export class Agora implements OnInit {
  /** Signal holding the list of all participants. */
  participants = signal<any[]>([]);

  /** Signal indicating whether the data is currently loading. */
  isLoading = signal(true);

  /** Signal holding the current search query entered by the user. */
  searchQuery = signal('');

  /** Signal holding the search results based on the search query. */
  searchResults = signal<any[]>([]);

  /** Signal indicating whether the current user is an administrator. */
  isAdmin = signal(false);

  /**
   * Initializes a new instance of the Agora component.
   *
   * @param router - The Angular router for navigation.
   * @param chironApi - The API service to communicate with the backend.
   * @param authService - The authentication service to retrieve user credentials.
   */
  constructor(
    private router: Router,
    public chironApi: ChironApi,
    private authService: AuthService
  ) {}

  /**
   * Lifecycle hook that is called after data-bound properties of a directive are initialized.
   * Loads the participants list and verifies the current user's administrator status.
   */
  ngOnInit() {
    this.loadParticipants();

    const myUsername = this.authService.getUsername();
    if (myUsername) {
      this.chironApi.getProfile(myUsername, myUsername).subscribe({
        next: (profile) => {
          if (profile && profile.isAdmin) {
            this.isAdmin.set(true);
          }
        },
        error: () => console.log("Erreur lors de la récupération du profil")
      });
    }
  }

  /**
   * Loads the list of all participants from the backend API.
   * Updates the `participants` signal upon success.
   */
  loadParticipants() {
    const myUsername = this.authService.getUsername() || '';
    this.isLoading.set(true);

    this.chironApi.getAgoraParticipants(myUsername).subscribe({
      next: (data) => {
        this.participants.set(data);
        this.isLoading.set(false);
      },
      error: (err) => {
        console.error("Erreur chargement Agora", err);
        this.isLoading.set(false);
      }
    });
  }

  /**
   * Searches for profiles matching the current `searchQuery`.
   * Updates the `searchResults` signal with the retrieved profiles.
   */
  searchProfiles() {
    const query = this.searchQuery().trim();
    if (!query) {
      this.searchResults.set([]);
      return;
    }

    const myUsername = this.authService.getUsername() || '';

    this.chironApi.searchProfiles(query, myUsername).subscribe({
      next: (results) => this.searchResults.set(results),
      error: (err) => console.error("Erreur de recherche", err)
    });
  }

  /**
   * Navigates to the detailed profile view of a specific user.
   * Clears the current search state before navigating.
   *
   * @param username - The username of the profile to navigate to.
   */
  goToProfile(username: string) {
    this.searchQuery.set('');
    this.searchResults.set([]);
    this.router.navigate(['/profile', username]);
  }

  /**
   * Navigates the user back to the chat interface.
   */
  goBack() {
    this.router.navigate(['/chat']);
  }
}
