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
  cardioType: string | null;
}

/** Préférences de calcul du tonnage de l'utilisateur (conventions de saisie du poids). */
export interface TrainingPrefs {
  halteresParImplement: boolean;
  machineParCote: boolean;
}

/** Fournisseur d'IA du coach Chiron, choisi par l'utilisateur. */
export type AiProvider = 'MISTRAL' | 'GEMINI';
export interface AiProviderPref {
  provider: AiProvider;
  /** true si l'agent Gemini est réellement provisionné côté serveur (clé configurée). */
  geminiAvailable: boolean;
}

/** Réponse du coach IA, avec l'id de la conversation à laquelle l'échange est rattaché. */
export interface ChatResponse {
  conversationId: number;
  reply: string;
}

/** Résumé d'une conversation pour le menu d'historique. */
export interface ConversationSummary {
  id: number;
  titre: string | null;
  updatedAt: string;
}

/** Un message persisté d'une conversation (role = 'USER' | 'AI'). */
export interface ConversationMessageDto {
  role: 'USER' | 'AI';
  content: string;
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
   * @param message        The content of the message.
   * @param conversationId The conversation to append to, or null to start a new one.
   * @return An Observable emitting the AI's reply and its conversation id.
   */
  sendMessage(message: string, conversationId: number | null, language: string) {
    return this.http.post<ChatResponse>(`${this.apiUrl}/chat`, {
      message,
      conversationId,
      language
    });
  }

  /**
   * Triggers the termination of the current workout session via the AI backend.
   *
   * @param conversationId The conversation to append to, or null.
   * @return An Observable emitting the backend's confirmation response.
   */
  endSession(conversationId: number | null, language: string) {
    return this.http.post<ChatResponse>(`${this.apiUrl}/end-session`, {
      conversationId,
      language
    });
  }

  /** Liste les conversations de l'utilisateur, de la plus récente à la plus ancienne. */
  listConversations(): Observable<ConversationSummary[]> {
    return this.http.get<ConversationSummary[]>(`${this.apiUrl}/conversations`);
  }

  /** Récupère les messages d'une conversation (pour la recharger). */
  getConversationMessages(id: number): Observable<ConversationMessageDto[]> {
    return this.http.get<ConversationMessageDto[]>(`${this.apiUrl}/conversations/${id}/messages`);
  }

  /** Supprime une conversation. */
  deleteConversation(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/conversations/${id}`);
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
   * @param username     The requesting user's username (authenticated caller).
   * @param programmeDto The data transfer object representing the program.
   * @param forUsername  Optional athlete the programme is being created for, when a coach
   *                     creates on someone else's behalf. Ignored on update.
   * @return An Observable emitting the server response confirmation.
   */
  sauvegarderProgramme(username: string, programmeDto: any, forUsername?: string) {
    let url = `${this.apiUrl}/programmes?username=${encodeURIComponent(username)}`;
    if (forUsername && forUsername !== username) {
      url += `&forUsername=${encodeURIComponent(forUsername)}`;
    }
    return this.http.post<string>(url, programmeDto, { responseType: 'text' as 'json' });
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
   * Persists a new manual display order for the user's programmes (drag-and-drop reorder).
   *
   * @param username   The requesting user's username.
   * @param orderedIds The programme IDs in the desired display order (first ID → top).
   * @return An Observable indicating the completion of the reorder request.
   */
  updateProgrammesOrder(username: string, orderedIds: number[]): Observable<any> {
    return this.http.put(`${this.apiUrl}/programmes/order?username=${encodeURIComponent(username)}`, orderedIds);
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

  getExerciceById(id: number): Observable<ExerciceDefinitionDto> {
    return this.http.get<ExerciceDefinitionDto>(`${this.apiUrl}/exercices/${id}`);
  }

  getExerciceGifUrl(id: number): string {
    return `${this.apiUrl}/exercices/${id}/gif`;
  }

  changePassword(req: { currentPassword: string; newPassword: string }): Observable<void> {
    return this.http.put<void>(`${this.apiUrl}/settings/password`, req);
  }

  getSettingsInfo(): Observable<{ username: string; email: string | null; prenom: string | null; nom: string | null }> {
    return this.http.get<{ username: string; email: string | null; prenom: string | null; nom: string | null }>(`${this.apiUrl}/settings/me`);
  }

  changeIdentity(req: { prenom: string | null; nom: string | null }): Observable<void> {
    return this.http.put<void>(`${this.apiUrl}/settings/identity`, req);
  }

  changeEmail(req: { newEmail: string }): Observable<void> {
    return this.http.put<void>(`${this.apiUrl}/settings/email`, req);
  }

  changeUsername(req: { newUsername: string }): Observable<{ token: string }> {
    return this.http.put<{ token: string }>(`${this.apiUrl}/settings/username`, req);
  }

  /** Préférences de calcul du tonnage (conventions de saisie du poids). */
  getTrainingPrefs(): Observable<TrainingPrefs> {
    return this.http.get<TrainingPrefs>(`${this.apiUrl}/settings/training-prefs`);
  }

  updateTrainingPrefs(prefs: TrainingPrefs): Observable<void> {
    return this.http.put<void>(`${this.apiUrl}/settings/training-prefs`, prefs);
  }

  /** Fournisseur d'IA du coach (Mistral ou Gemini). */
  getAiProvider(): Observable<AiProviderPref> {
    return this.http.get<AiProviderPref>(`${this.apiUrl}/settings/ai-provider`);
  }

  updateAiProvider(provider: AiProvider): Observable<void> {
    return this.http.put<void>(`${this.apiUrl}/settings/ai-provider`, { provider });
  }

  forgotPassword(email: string): Observable<void> {
    return this.http.post<void>(`${this.apiUrl}/auth/forgot-password`, { email });
  }

  resetPassword(token: string, newPassword: string): Observable<void> {
    return this.http.post<void>(`${this.apiUrl}/auth/reset-password`, { token, newPassword });
  }

  // --- Liaison Olympus (nutrition) ---

  getNutritionStatus(): Observable<NutritionLinkStatus> {
    return this.http.get<NutritionLinkStatus>(`${this.apiUrl}/nutrition/status`);
  }

  linkOlympus(pseudo: string, password: string): Observable<NutritionLinkStatus> {
    return this.http.post<NutritionLinkStatus>(`${this.apiUrl}/nutrition/link`, { pseudo, password });
  }

  unlinkOlympus(): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/nutrition/link`);
  }

  /** Token de liaison pour l'entrée directe (« handoff ») dans la PWA Olympus. */
  getOlympusHandoff(): Observable<OlympusHandoff> {
    return this.http.get<OlympusHandoff>(`${this.apiUrl}/nutrition/handoff`);
  }

  // --- Liaison Fitbit (données santé) ---

  getFitbitStatus(): Observable<FitbitLinkStatus> {
    return this.http.get<FitbitLinkStatus>(`${this.apiUrl}/fitbit/status`);
  }

  getFitbitAuthorizeUrl(): Observable<{ authorizeUrl: string }> {
    return this.http.get<{ authorizeUrl: string }>(`${this.apiUrl}/fitbit/authorize-url`);
  }

  unlinkFitbit(): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/fitbit/link`);
  }

  getFitbitDashboard(days: number = 7): Observable<FitbitDashboard> {
    return this.http.get<FitbitDashboard>(`${this.apiUrl}/fitbit/dashboard?days=${days}`);
  }

  getActivites(jours: number = 400, source?: 'CHIRON_MUSCU' | 'GOOGLE_DETECTE'): Observable<SanteActiviteDto[]> {
    let url = `${this.apiUrl}/sante/activites?jours=${jours}`;
    if (source) url += `&source=${source}`;
    return this.http.get<SanteActiviteDto[]>(url);
  }

  // --- Profil sportif enrichi ---

  getProfileSetup(): Observable<UserProfileSetup> {
    return this.http.get<UserProfileSetup>(`${this.apiUrl}/profile-setup`);
  }

  saveProfileSetup(setup: UserProfileSetup): Observable<UserProfileSetup> {
    return this.http.put<UserProfileSetup>(`${this.apiUrl}/profile-setup`, setup);
  }

  // --- Statistiques ---

  getStatsOverview(): Observable<StatsOverview> {
    return this.http.get<StatsOverview>(`${this.apiUrl}/stats/overview`);
  }

  getStatsVolume(weeks: number = 12): Observable<WeeklyVolumePoint[]> {
    return this.http.get<WeeklyVolumePoint[]>(`${this.apiUrl}/stats/volume?weeks=${weeks}`);
  }

  getStatsMuscles(days: number = 30): Observable<MuscleStats> {
    return this.http.get<MuscleStats>(`${this.apiUrl}/stats/muscles?days=${days}`);
  }

  getStatsExercises(): Observable<ExerciseListItem[]> {
    return this.http.get<ExerciseListItem[]>(`${this.apiUrl}/stats/exercises`);
  }

  getStatsExerciseProgress(nom: string): Observable<ExerciseProgressPoint[]> {
    return this.http.get<ExerciseProgressPoint[]>(`${this.apiUrl}/stats/exercises/progress?nom=${encodeURIComponent(nom)}`);
  }

  getStatsNutrition(days: number = 30): Observable<NutritionStats> {
    return this.http.get<NutritionStats>(`${this.apiUrl}/stats/nutrition?days=${days}`);
  }

  getStatsBodyweight(days: number = 90): Observable<BodyweightStats> {
    return this.http.get<BodyweightStats>(`${this.apiUrl}/stats/bodyweight?days=${days}`);
  }

  getRecovery(days: number = 30): Observable<RecoveryPoint[]> {
    return this.http.get<RecoveryPoint[]>(`${this.apiUrl}/etat-journalier/recent?days=${days}`);
  }

  getStatsBodyComposition(days: number = 180): Observable<BodyCompositionStats> {
    return this.http.get<BodyCompositionStats>(`${this.apiUrl}/stats/body-composition?days=${days}`);
  }

  /** Upload manuel d'un rapport PDF Visbody (test / fallback de l'ingestion par email). */
  uploadVisbodyPdf(file: File): Observable<VisbodyImportResult> {
    const form = new FormData();
    form.append('file', file);
    return this.http.post<VisbodyImportResult>(`${this.apiUrl}/visbody/import`, form);
  }

  /** Upload manuel d'un export CSV Boditrax (alimente la page Composition). */
  uploadBoditraxCsv(file: File): Observable<VisbodyImportResult> {
    const form = new FormData();
    form.append('file', file);
    return this.http.post<VisbodyImportResult>(`${this.apiUrl}/boditrax/import`, form);
  }
}

export interface NutritionLinkStatus {
  linked: boolean;
  expired: boolean;
  olympusUsername: string | null;
  linkedAt: string | null;
  expiresAt: string | null;
}

export interface OlympusHandoff {
  token: string;
}

export interface FitbitLinkStatus {
  linked: boolean;
  needsReconnect: boolean;
  fitbitUserId: string | null;
  scope: string | null;
  linkedAt: string | null;
}

export interface FitbitDayPoint {
  date: string;
  steps: number | null;
  sleepHours: number | null;
}

export interface FitbitDashboard {
  linked: boolean;
  needsReconnect: boolean;
  dataAvailable: boolean;
  stepsToday: number | null;
  activeMinutesToday: number | null;
  distanceTodayKm: number | null;
  caloriesToday: number | null;
  sleepHoursLastNight: number | null;
  restingHeartRate: number | null;
  days: FitbitDayPoint[];
}

export type Sexe = 'HOMME' | 'FEMME' | 'AUTRE';

export type NiveauExperience = 'DEBUTANT' | 'INTERMEDIAIRE' | 'AVANCE' | 'EXPERT';

export type ObjectifPrincipal =
  | 'FORCE'
  | 'HYPERTROPHIE'
  | 'ENDURANCE'
  | 'PERTE_DE_GRAS'
  | 'MAINTIEN'
  | 'SANTE_GENERALE';

export type TypeEquipement =
  | 'POIDS_DU_CORPS'
  | 'HALTERES'
  | 'BARRE'
  | 'MACHINE'
  | 'POULIE'
  | 'KETTLEBELL'
  | 'ELASTIQUE'
  | 'BARRE_FIXE'
  | 'ANNEAUX'
  | 'AUTRE';

export interface UserProfileSetup {
  isOnboarded: boolean;
  dateNaissance: string | null;          // ISO YYYY-MM-DD
  sexe: Sexe | null;
  tailleCm: number | null;
  poidsCorps: number | null;
  niveauExperience: NiveauExperience | null;
  objectifPrincipal: ObjectifPrincipal | null;
  frequenceVisee: number | null;
  materielDisponible: TypeEquipement[];
  blessures: string | null;
  preferences: string | null;
}

// --- Statistiques ---

export interface StatsOverview {
  seances30: number;
  seancesTotal: number;
  tonnageSemaine: number;
  streakSemaines: number;
  poidsCorps: number | null;
  tier: string;
  tierLevel: number;
  tierCategorie: string;
  dureeMoyenneMin: number | null;
}

export interface WeeklyVolumePoint {
  semaine: string;   // date ISO du lundi
  label: string;     // « jj/MM »
  tonnage: number;
  nbSeances: number;
  nbSeries: number;
  dureeMoyenneMin: number | null;
}

export interface MuscleStat {
  muscle: string;
  tonnage: number;
  nbSeries: number;
  nbSeances: number;
}

export interface MuscleStats {
  muscles: MuscleStat[];
  negliges: string[];
}

export interface ExerciseListItem {
  nom: string;
  nbSeances: number;
  derniereSeance: string | null;
}

export interface ExerciseProgressPoint {
  date: string;
  chargeMax: number;
  e1rm: number;
  volume: number;
}

export interface NutritionPoint {
  date: string;
  kcal: number | null;
  proteines: number | null;
  glucides: number | null;
  lipides: number | null;
  targetKcal: number | null;
  pas: number | null;
}

export interface NutritionStats {
  linked: boolean;
  jours: NutritionPoint[];
  moyKcal: number | null;
  moyProteines: number | null;
  moyGlucides: number | null;
  moyLipides: number | null;
  moyTargetKcal: number | null;
}

export interface BodyweightPoint {
  date: string;
  poids: number;
}

export interface BodyweightStats {
  linked: boolean;
  points: BodyweightPoint[];
}

export interface BodyCompositionPoint {
  date: string;
  note: number | null;
  poids: number | null;
  masseMusculaire: number | null;
  mms: number | null;
  mgc: number | null;
  mmc: number | null;
  tgcPct: number | null;
  imc: number | null;
  rth: number | null;
  mbKcal: number | null;
  ageMetabolique: number | null;
  graisseViscerale: number | null;
  eauTotale: number | null;
  eauIntra: number | null;
  eauExtra: number | null;
  ratioEcwTbw: number | null;
  masseProteine: number | null;
  selInorganique: number | null;
  // Masse grasse par segment (kg).
  mgcBrasGauche: number | null;
  mgcBrasDroit: number | null;
  mgcTronc: number | null;
  mgcJambeGauche: number | null;
  mgcJambeDroite: number | null;
  // Masse musculaire par segment (kg).
  muscleBrasGauche: number | null;
  muscleBrasDroit: number | null;
  muscleTronc: number | null;
  muscleJambeGauche: number | null;
  muscleJambeDroite: number | null;
}

export interface BodyCompositionStats {
  hasData: boolean;
  points: BodyCompositionPoint[];
}

export interface VisbodyImportResult {
  outcome: 'IMPORTED' | 'DUPLICATE' | 'USER_NOT_FOUND' | 'INVALID';
  detail: string;
}

export type TypeActivite = 'MUSCULATION' | 'MARCHE' | 'COURSE' | 'VELO' | 'FOOTBALL' | 'SPORT_AUTRE';

export interface SanteActiviteDto {
  id: number;
  source: 'CHIRON_MUSCU' | 'GOOGLE_DETECTE';
  typeActivite: TypeActivite;
  startTime: string;
  endTime: string;
  calories: number | null;
  fcMoyenne: number | null;
  fcMin: number | null;
  fcMax: number | null;
  minutesZoneBasse: number | null;
  minutesZoneBruleuse: number | null;
  minutesZoneCardio: number | null;
  minutesZonePic: number | null;
  minutesZoneActive: number | null;
  chargeCardio: number | null;
  seanceId: number | null;
  enrichissementEnCours: boolean;
}

export interface RecoveryPoint {
  date: string;
  sommeilHeures: number | null;
  fatigue: number | null;
  courbatures: number | null;
  stress: number | null;
  energie: number | null;
  notes: string | null;
}

export interface PerformanceExercise {
  exerciseType: string;
  nom: string;
  poids: number | null;
  nombreReps: number | null;
  rm1Estime: number | null;
  ratioPerformance: number | null;
  tier: string;
  tierLevel: number;
  tierCategorie: string;
  recordedAt: string | null;
}

export interface PerformanceSummary {
  overallTier: string;
  overallTierLevel: number;
  overallTierCategorie: string;
  poidsCorps: number | null;
  exercises: PerformanceExercise[];
}
