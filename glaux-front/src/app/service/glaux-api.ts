import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface SanteResumeDto {
  linked: boolean;
  needsReconnect: boolean;
  date: string | null;
  pas: number | null;
  distanceM: number | null;
  caloriesTotales: number | null;
  caloriesActives: number | null;
  minutesZoneActive: number | null;
  fcRepos: number | null;
  scoreSommeil: number | null;
  chargeCardioHebdo: number | null;
  scorePreparation: number | null;
}

export interface SanteJourDto {
  date: string;
  pas: number | null;
  distanceM: number | null;
  caloriesTotales: number | null;
  caloriesActives: number | null;
  minutesZoneActive: number | null;
  minutesZoneBruleuse: number | null;
  minutesZoneCardio: number | null;
  minutesZonePic: number | null;
  fcRepos: number | null;
  fcMin: number | null;
  fcMoyenne: number | null;
  fcMax: number | null;
  vfcMs: number | null;
  chargeCardio: number | null;
  frequenceRespiratoire: number | null;
}

export interface SanteFcPointDto {
  horodatage: string;
  fcMin: number | null;
  fcMoyenne: number | null;
  fcMax: number | null;
}

export interface SanteFcJourDto {
  date: string;
  fcMin: number | null;
  fcMoyenne: number | null;
  fcMax: number | null;
  fcRepos: number | null;
}

export interface SanteSommeilDto {
  date: string;
  debut: string;
  fin: string;
  sieste: boolean;
  stadesDisponibles: boolean;
  minutesEndormi: number | null;
  minutesEveille: number | null;
  minutesAvantEndormissement: number | null;
  minutesApresReveil: number | null;
  minutesProfond: number | null;
  minutesLeger: number | null;
  minutesParadoxal: number | null;
  minutesAgite: number | null;
  nbReveils: number | null;
  fcSommeilMoyenne: number | null;
  score: number | null;
  scoreDuree: number | null;
  scoreComposition: number | null;
  scoreRestauration: number | null;
}

export interface SanteCardioHebdoDto {
  semaineDebut: string;
  chargeCardio: number | null;
  cibleBasse: number | null;
  cibleHaute: number | null;
  minutesZoneActive: number | null;
}

export type NoctuaBriefingType = 'REVEIL' | 'ACTIVITE' | 'COUCHER';

export interface NoctuaBriefingDto {
  id: number;
  type: NoctuaBriefingType;
  dateReference: string;
  createdAt: string;
  lu: boolean;
  premierParagraphe: string;
}

export interface NoctuaConversationMessageDto {
  role: 'USER' | 'AI';
  content: string;
}

export interface NoctuaBriefingDetailDto {
  briefing: NoctuaBriefingDto;
  messages: NoctuaConversationMessageDto[];
}

export interface NoctuaChatResponse {
  conversationId: number;
  reply: string;
}

export interface NoctuaNonLusDto {
  count: number;
}

export interface SanteSyncEtatDto {
  typeDonnee: string;
  derniereDateSynchronisee: string | null;
  derniereExecution: string | null;
  statut: string;
  message: string | null;
}

export type TypeActivite =
  'MUSCULATION' | 'MARCHE' | 'COURSE' | 'VELO' | 'FOOTBALL' | 'SPORT_AUTRE';

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

export interface SeuilsCardiaquesDto {
  modere: number;
  intense: number;
  maximum: number;
}

export interface SanteActiviteDetailDto {
  activite: SanteActiviteDto;
  pointsFrequenceCardiaque: SanteFcPointDto[];
  seuils: SeuilsCardiaquesDto;
}

@Injectable({ providedIn: 'root' })
export class GlauxApi {
  private apiUrl = `${environment.apiUrl}/sante`;

  constructor(private http: HttpClient) {}

  getResume(): Observable<SanteResumeDto> {
    return this.http.get<SanteResumeDto>(`${this.apiUrl}/resume`);
  }

  getJours(jours = 30): Observable<SanteJourDto[]> {
    return this.http.get<SanteJourDto[]>(`${this.apiUrl}/jours?jours=${jours}`);
  }

  getFrequenceCardiaqueJour(date: string): Observable<SanteFcPointDto[]> {
    return this.http.get<SanteFcPointDto[]>(`${this.apiUrl}/frequence-cardiaque?date=${date}`);
  }

  getFrequenceCardiaquePlage(jours = 30): Observable<SanteFcJourDto[]> {
    return this.http.get<SanteFcJourDto[]>(
      `${this.apiUrl}/frequence-cardiaque/plage?jours=${jours}`,
    );
  }

  getSommeil(jours = 30): Observable<SanteSommeilDto[]> {
    return this.http.get<SanteSommeilDto[]>(`${this.apiUrl}/sommeil?jours=${jours}`);
  }

  getCardioHebdo(semaines = 12): Observable<SanteCardioHebdoDto[]> {
    return this.http.get<SanteCardioHebdoDto[]>(`${this.apiUrl}/cardio-hebdo?semaines=${semaines}`);
  }

  getSyncEtat(): Observable<SanteSyncEtatDto[]> {
    return this.http.get<SanteSyncEtatDto[]>(`${this.apiUrl}/sync`);
  }

  forcerSync(): Observable<SanteSyncEtatDto[]> {
    return this.http.post<SanteSyncEtatDto[]>(`${this.apiUrl}/sync`, {});
  }

  getActivites(jours = 30): Observable<SanteActiviteDto[]> {
    return this.http.get<SanteActiviteDto[]>(`${this.apiUrl}/activites?jours=${jours}`);
  }

  getActiviteDetail(id: number): Observable<SanteActiviteDetailDto> {
    return this.http.get<SanteActiviteDetailDto>(`${this.apiUrl}/activites/${id}`);
  }

  getNoctuaBriefingParActivite(activiteId: number): Observable<NoctuaBriefingDto> {
    return this.http.get<NoctuaBriefingDto>(
      `${environment.apiUrl}/noctua/briefings/par-activite/${activiteId}`,
    );
  }

  getNoctuaBriefings(jours = 14): Observable<NoctuaBriefingDto[]> {
    return this.http.get<NoctuaBriefingDto[]>(
      `${environment.apiUrl}/noctua/briefings?jours=${jours}`,
    );
  }

  getNoctuaBriefing(id: number): Observable<NoctuaBriefingDetailDto> {
    return this.http.get<NoctuaBriefingDetailDto>(`${environment.apiUrl}/noctua/briefings/${id}`);
  }

  envoyerMessageNoctua(
    id: number,
    message: string,
    language: string,
  ): Observable<NoctuaChatResponse> {
    return this.http.post<NoctuaChatResponse>(
      `${environment.apiUrl}/noctua/briefings/${id}/messages`,
      {
        message,
        language,
      },
    );
  }

  marquerBriefingLu(id: number): Observable<void> {
    return this.http.post<void>(`${environment.apiUrl}/noctua/briefings/${id}/lu`, {});
  }

  getNoctuaNonLus(): Observable<NoctuaNonLusDto> {
    return this.http.get<NoctuaNonLusDto>(`${environment.apiUrl}/noctua/non-lus`);
  }
}
