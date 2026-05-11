import { Component, OnInit, signal, ViewChild, ElementRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, ActivatedRoute } from '@angular/router';
import { ChironApi } from '../../service/chiron-api';
import { AuthService } from '../../service/auth.service';

/**
 * Component representing the user profile view.
 * Handles the display of a user's statistics, history, programs, and settings.
 * Also manages avatar uploads and coaching relationship modifications.
 */
@Component({
  selector: 'app-profile',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './profile.html'
})
export class Profile implements OnInit {

  /** Reference to the hidden file input element used for avatar uploads. */
  @ViewChild('fileInput') fileInput!: ElementRef;

  /** The ID (username) of the profile currently being viewed, if provided via route params. */
  userId: string | null = null;

  /** Indicates whether the currently viewed profile belongs to the authenticated user. */
  isMyProfile = signal(true);

  /** The current search string entered in the user search bar. */
  searchQuery = signal('');

  /** The list of profiles matching the current search query. */
  searchResults = signal<any[]>([]);

  /** Data object containing the profile statistics, history, and status. */
  athleteProfile = signal<any>(null);

  /** Loading state indicator. */
  isLoading = signal(true);

  /** The username of the currently authenticated user. */
  myUsername = signal<string | null>(null);

  /** Indicates whether the currently authenticated user has administrator privileges. */
  isAdmin = signal(false);

  /**
   * Initializes a new instance of the Profile component.
   *
   * @param route       ActivatedRoute to extract route parameters.
   * @param router      Router for application navigation.
   * @param chironApi   Service for API interactions.
   * @param authService Service for authentication state management.
   */
  constructor(
    private route: ActivatedRoute,
    private router: Router,
    public chironApi: ChironApi,
    private authService: AuthService
  ) {}

  /**
   * Lifecycle hook triggered on initialization.
   * Checks the user's role and determines which profile to load based on the route parameters.
   */
  ngOnInit() {
    const currentUsername = this.authService.getUsername();
    this.myUsername.set(currentUsername);

    if (currentUsername) {
      this.chironApi.getProfile(currentUsername, currentUsername).subscribe({
        next: (profile) => {
          if (profile && profile.isAdmin) {
            this.isAdmin.set(true);
          }
        },
        error: () => console.log("Erreur lors de la récupération de mon propre profil pour vérifier les droits admin")
      });
    }

    this.route.paramMap.subscribe(params => {
      this.userId = params.get('id');

      if (this.userId && this.userId !== currentUsername) {
        this.isMyProfile.set(false);
        this.loadProfile(this.userId, currentUsername);
      } else {
        this.isMyProfile.set(true);
        if (currentUsername) {
          this.loadProfile(currentUsername, currentUsername);
        }
      }
    });
  }

  /**
   * Fetches the profile data for a specific user from the API.
   *
   * @param username        The username of the profile to retrieve.
   * @param requestUsername The username of the user initiating the request.
   */
  loadProfile(username: string, requestUsername: string | null = null) {
    this.isLoading.set(true);
    if (!requestUsername) {
      requestUsername = this.myUsername() || '';
    }

    this.chironApi.getProfile(username, requestUsername).subscribe({
      next: (data) => {
        this.athleteProfile.set(data);
        this.isLoading.set(false);
      },
      error: (err) => {
        console.error("Erreur chargement profil", err);
        this.isLoading.set(false);
        alert("Profil introuvable ou privé.");
        this.router.navigate(['/chat']);
      }
    });
  }

  /**
   * Triggers a search for other user profiles based on the current search query.
   */
  searchProfiles() {
    const query = this.searchQuery().trim();
    if (!query) {
      this.searchResults.set([]);
      return;
    }

    const currentUsername = this.myUsername();
    if (!currentUsername) return;

    this.chironApi.searchProfiles(query, currentUsername).subscribe({
      next: (results) => this.searchResults.set(results),
      error: (err) => console.error("Erreur de recherche", err)
    });
  }

  /**
   * Navigates to a specific user's profile view.
   * Reloads the page if navigating to the same profile to ensure data freshness.
   *
   * @param username The username of the profile to navigate to.
   */
  goToProfile(username: string) {
    this.searchQuery.set('');
    this.searchResults.set([]);

    const currentUsername = this.myUsername();
    if (username === currentUsername) {
       this.router.navigate(['/profile']).then(() => window.location.reload());
    } else {
       this.router.navigate(['/profile', username]);
    }
  }

  /**
   * Programmatically opens the hidden file input dialog for avatar selection.
   */
  triggerFileInput() {
    if (this.fileInput && this.fileInput.nativeElement) {
      this.fileInput.nativeElement.click();
    }
  }

  /**
   * Handles the selection of a new avatar image and uploads it to the backend.
   *
   * @param event The file selection event.
   */
  onFileSelected(event: any) {
    const file: File = event.target.files[0];
    if (file) {
      const currentUsername = this.myUsername();
      if (!currentUsername) return;

      this.isLoading.set(true);
      const formData = new FormData();
      formData.append('file', file);

      this.chironApi.updateProfileIcon(currentUsername, formData).subscribe({
        next: () => {
          window.location.reload();
        },
        error: (err) => {
          console.error("Erreur upload image", err);
          alert("Erreur lors de la mise à jour de l'image.");
          this.isLoading.set(false);
        }
      });
    }
  }

  /**
   * Toggles the public visibility setting of the authenticated user's profile.
   */
  toggleVisibility() {
    const currentUsername = this.myUsername();
    if (!currentUsername) return;

    const newVisibility = !this.athleteProfile().isPublic;
    this.athleteProfile.update(profile => ({ ...profile, isPublic: newVisibility }));

    this.chironApi.updateProfileVisibility(currentUsername, newVisibility).subscribe({
      error: (err) => {
        console.error("Erreur mise à jour visibilité", err);
        this.athleteProfile.update(profile => ({ ...profile, isPublic: !newVisibility }));
      }
    });
  }

  /**
   * Navigates to the detailed view of a specific workout program.
   * Configures query parameters to indicate read-only status or editing privileges for coaches.
   *
   * @param progId The ID of the program to view.
   */
  viewProgramme(progId: number) {
    const queryParams: any = { from: 'journal' };

    if (!this.isMyProfile() && !this.athleteProfile().amICoach) {
      queryParams.external = 'true';
    }

    if (!this.isMyProfile() && this.athleteProfile().amICoach) {
        queryParams.coachEdit = 'true';
        delete queryParams.from;
    }

    this.router.navigate(['/session', progId], { queryParams });
  }

  /**
   * Copies a program from the viewed profile into the authenticated user's own profile.
   *
   * @param progId The ID of the program to copy.
   * @param event  The optional DOM click event to prevent propagation.
   */
  copyProgramme(progId: number, event?: Event) {
    if (event) {
        event.stopPropagation();
    }

    const currentUsername = this.myUsername();
    if (!currentUsername) return;

    this.isLoading.set(true);
    this.chironApi.copyProgrammeToMyProfile(progId, currentUsername).subscribe({
      next: () => {
        alert("Programme copié avec succès dans ton sanctuaire !");
        this.isLoading.set(false);
      },
      error: (err) => {
        console.error("Erreur copie", err);
        alert("Impossible de copier ce programme.");
        this.isLoading.set(false);
      }
    });
  }

  /**
   * Grants or revokes coaching privileges for the currently viewed user over the authenticated user.
   */
  toggleCoachRights() {
    const currentUsername = this.myUsername();
    const targetUsername = this.athleteProfile()?.username;

    if (!currentUsername || !targetUsername || this.isMyProfile()) return;

    const currentlyCoach = this.athleteProfile().isMyCoach;

    if (currentlyCoach) {
      this.chironApi.removeCoach(currentUsername, targetUsername).subscribe({
        next: () => {
          this.athleteProfile.update(p => {
             return { ...p, isMyCoach: false };
          });
          alert(`${targetUsername} n'a plus les droits de coach sur tes programmes.`);
        },
        error: (err) => {
            console.error("Erreur remove coach", err);
            alert("Erreur lors de la révocation des droits de coach.");
        }
      });
    } else {
      this.chironApi.addCoach(currentUsername, targetUsername).subscribe({
        next: () => {
          this.athleteProfile.update(p => {
             return { ...p, isMyCoach: true };
          });
          alert(`${targetUsername} est maintenant autorisé à modifier tes programmes !`);
        },
        error: (err) => {
            console.error("Erreur add coach", err);
            alert("Erreur lors de l'attribution des droits de coach.");
        }
      });
    }
  }

  /**
   * Navigates back to the main chat interface.
   */
  goBack() {
    this.router.navigate(['/chat']);
  }
}
