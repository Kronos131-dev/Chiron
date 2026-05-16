import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface ExerciceDefinitionDto {
  id: number;
  nomFr: string | null;
  nomEn: string;
  imageUrl: string | null;
  imageUrl2: string | null;
  musclePrincipal: string | null;
  musclesSecondaires: string[];
  typeEquipement: string | null;
  difficulte: string | null;
  descriptionFr: string | null;
  descriptionEn: string | null;
}

/**
 * Interface defining the structure of an aggregated exercise performance summary.
 */
export interface Exercise {
  nom: string;
  series: number;
  repsMoyennes: number;
  chargeMax: number;
}

/**
 * Core API service handling all HTTP communication with the Chiron backend.
 */
@Injectable({
  providedIn: 'root'
})
export class ChironApi {
  private apiUrl = environment.apiUrl;

  /**
   * Initializes the API service.
   *
   * @param http The Angular HttpClient instance used for requests.
   */
  constructor(private http: HttpClient) { }

  /**
   * Constructs the full URL for retrieving a specific image resource.
   *
   * @param iconName The filename of the requested icon/image.
   * @return The complete absolute URL.
   */
  getImageUrl(iconName: string): string {
    return `${this.apiUrl}/images/${encodeURIComponent(iconName)}`;
  }

  /**
   * Sends a user message to the AI coach via the chat endpoint.
   *
   * @param username The username of the sender.
   * @param message  The content of the message.
   * @return An Observable emitting the AI's textual response.
   */
  sendMessage(username: string, message: string) {
    return this.http.post(`${this.apiUrl}/chat`, {
      username: username,
      message: message
    }, { responseType: 'text' as 'json' });
  }

  /**
   * Triggers the termination of the current workout session via the AI backend.
   *
   * @param username The username ending their session.
   * @return An Observable emitting the backend's confirmation response.
   */
  endSession(username: string) {
    return this.http.post(`${this.apiUrl}/end-session`, {
      username: username
    }, { responseType: 'text' as 'json' });
  }

  /**
   * Retrieves the summary of the user's workout for the current day.
   *
   * @return An Observable emitting an array of performed exercises.
   */
  getTodaySession(): Observable<Exercise[]> {
    return this.http.get<Exercise[]>(`${this.apiUrl}/journal/today`);
  }

  /**
   * Fetches the complete workout history for a given user.
   *
   * @param username The target user's username.
   * @return An Observable emitting the list of historical workout sessions.
   */
  getHistorique(username: string) {
    return this.http.get<any[]>(`${this.apiUrl}/journal/historique?username=${encodeURIComponent(username)}`);
  }

  /**
   * Retrieves all workout programs (templates) belonging to a user.
   *
   * @param username The target user's username.
   * @return An Observable emitting the list of workout programs.
   */
  getProgrammes(username: string) {
    return this.http.get<any[]>(`${this.apiUrl}/programmes?username=${encodeURIComponent(username)}`);
  }

  /**
   * Saves a new or updated workout program for a user.
   *
   * @param username     The requesting user's username.
   * @param programmeDto The data transfer object representing the program.
   * @return An Observable emitting the server response confirmation.
   */
  sauvegarderProgramme(username: string, programmeDto: any) {
    return this.http.post<string>(`${this.apiUrl}/programmes?username=${encodeURIComponent(username)}`, programmeDto, { responseType: 'text' as 'json' });
  }

  /**
   * Retrieves a specific workout program by its ID.
   *
   * @param username The requesting user's username.
   * @param id       The unique ID of the program.
   * @return An Observable emitting the detailed program data.
   */
  getProgrammeById(username: string, id: string) {
    return this.http.get<any>(`${this.apiUrl}/programmes/${id}?username=${encodeURIComponent(username)}`);
  }

  /**
   * Deletes a specific workout program.
   *
   * @param id       The unique ID of the program to delete.
   * @param username The requesting user's username.
   * @return An Observable indicating the completion of the delete request.
   */
  deleteProgramme(id: number, username: string): Observable<any> {
    return this.http.delete(`${this.apiUrl}/programmes/${id}?username=${encodeURIComponent(username)}`);
  }

  /**
   * Fetches the detailed profile data of a specific user.
   *
   * @param username        The username of the target profile.
   * @param requestUsername The username of the user making the request.
   * @return An Observable emitting the profile data.
   */
  getProfile(username: string, requestUsername: string): Observable<any> {
    return this.http.get<any>(`${this.apiUrl}/profile/${encodeURIComponent(username)}?requestUsername=${encodeURIComponent(requestUsername)}`);
  }

  /**
   * Searches for user profiles matching a given query.
   *
   * @param query           The partial username to search for.
   * @param requestUsername The username of the user initiating the search.
   * @return An Observable emitting a list of matching profiles.
   */
  searchProfiles(query: string, requestUsername: string): Observable<any[]> {
    return this.http.get<any[]>(`${this.apiUrl}/profile/search?query=${encodeURIComponent(query)}&requestUsername=${encodeURIComponent(requestUsername)}`);
  }

  /**
   * Retrieves the list of users displayed in the public Agora.
   *
   * @param requestUsername The username of the user making the request.
   * @return An Observable emitting the list of Agora participants.
   */
  getAgoraParticipants(requestUsername: string): Observable<any[]> {
    return this.http.get<any[]>(`${this.apiUrl}/agora/participants?requestUsername=${encodeURIComponent(requestUsername)}`);
  }

  /**
   * Updates the public visibility state of the user's profile.
   *
   * @param username The username of the profile to update.
   * @param isPublic The desired visibility state (true for public, false for private).
   * @return An Observable indicating the completion of the update.
   */
  updateProfileVisibility(username: string, isPublic: boolean): Observable<any> {
    return this.http.put(`${this.apiUrl}/profile/${encodeURIComponent(username)}/visibility?isPublic=${isPublic}`, {});
  }

  /**
   * Uploads a new avatar image to update the user's profile icon.
   *
   * @param username The target username.
   * @param formData The FormData object containing the image file.
   * @return An Observable emitting the server response confirmation.
   */
  updateProfileIcon(username: string, formData: FormData): Observable<any> {
    return this.http.post(`${this.apiUrl}/profile/${encodeURIComponent(username)}/icon`, formData);
  }

  /**
   * Creates a copy of an existing program and assigns it to the target user.
   *
   * @param progId         The ID of the program to copy.
   * @param targetUsername The username of the user receiving the copy.
   * @return An Observable emitting the server response confirmation.
   */
  copyProgrammeToMyProfile(progId: number, targetUsername: string): Observable<any> {
    return this.http.post(`${this.apiUrl}/programmes/${progId}/copy?targetUsername=${encodeURIComponent(targetUsername)}`, {}, { responseType: 'text' as 'json' });
  }

  /**
   * Designates a specific user as a coach for the requesting user.
   *
   * @param username      The requesting user assigning the coach.
   * @param coachUsername The username of the designated coach.
   * @return An Observable indicating the completion of the assignment.
   */
  addCoach(username: string, coachUsername: string): Observable<any> {
    return this.http.post(`${this.apiUrl}/profile/${encodeURIComponent(username)}/coach/${encodeURIComponent(coachUsername)}`, {});
  }

  /**
   * Revokes the coaching privileges of a previously designated coach.
   *
   * @param username      The requesting user revoking the coach.
   * @param coachUsername The username of the coach being removed.
   * @return An Observable indicating the completion of the revocation.
   */
  removeCoach(username: string, coachUsername: string): Observable<any> {
    return this.http.delete(`${this.apiUrl}/profile/${encodeURIComponent(username)}/coach/${encodeURIComponent(coachUsername)}`);
  }

  /**
   * Deletes a user profile and all associated data.
   *
   * @param username The username of the profile to delete.
   * @return An Observable indicating the completion of the deletion.
   */
  deleteProfile(username: string): Observable<any> {
    return this.http.delete(`${this.apiUrl}/profile/${encodeURIComponent(username)}`);
  }

  getPerformanceSummary(username: string): Observable<any> {
    return this.http.get<any>(`${this.apiUrl}/performance/${encodeURIComponent(username)}`);
  }

  addPerformanceRecord(username: string, dto: { exerciseType: string; poids: number; nombreReps: number }): Observable<any> {
    return this.http.post<any>(`${this.apiUrl}/performance/${encodeURIComponent(username)}/record`, dto);
  }

  updateBodyweight(username: string, poidsCorps: number): Observable<any> {
    return this.http.put<any>(`${this.apiUrl}/performance/${encodeURIComponent(username)}/bodyweight`, { poidsCorps });
  }

  getPerformanceHistory(username: string, exerciseType: string): Observable<any[]> {
    return this.http.get<any[]>(`${this.apiUrl}/performance/${encodeURIComponent(username)}/history/${encodeURIComponent(exerciseType)}`);
  }

  searchExercices(q: string, muscle?: string, equipement?: string, difficulte?: string): Observable<ExerciceDefinitionDto[]> {
    let url = `${this.apiUrl}/exercices?q=${encodeURIComponent(q)}`;
    if (muscle) url += `&muscle=${encodeURIComponent(muscle)}`;
    if (equipement) url += `&equipement=${encodeURIComponent(equipement)}`;
    if (difficulte) url += `&difficulte=${encodeURIComponent(difficulte)}`;
    return this.http.get<ExerciceDefinitionDto[]>(url);
  }

  getExercices(muscle?: string, equipement?: string, difficulte?: string): Observable<ExerciceDefinitionDto[]> {
    let url = `${this.apiUrl}/exercices?`;
    if (muscle) url += `&muscle=${encodeURIComponent(muscle)}`;
    if (equipement) url += `&equipement=${encodeURIComponent(equipement)}`;
    if (difficulte) url += `&difficulte=${encodeURIComponent(difficulte)}`;
    return this.http.get<ExerciceDefinitionDto[]>(url);
  }

  getExerciceGifUrl(id: number): string {
    return `${this.apiUrl}/exercices/${id}/gif`;
  }

  changePassword(req: { currentPassword: string; newPassword: string }): Observable<void> {
    return this.http.put<void>(`${this.apiUrl}/settings/password`, req);
  }

  changeEmail(req: { currentPassword: string; newEmail: string }): Observable<void> {
    return this.http.put<void>(`${this.apiUrl}/settings/email`, req);
  }

  changeUsername(req: { currentPassword: string; newUsername: string }): Observable<{ token: string }> {
    return this.http.put<{ token: string }>(`${this.apiUrl}/settings/username`, req);
  }

  forgotPassword(email: string): Observable<void> {
    return this.http.post<void>(`${this.apiUrl}/auth/forgot-password`, { email });
  }

  resetPassword(token: string, newPassword: string): Observable<void> {
    return this.http.post<void>(`${this.apiUrl}/auth/reset-password`, { token, newPassword });
  }
}
