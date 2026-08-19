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

export interface SanteSyncEtatDto {
  typeDonnee: string;
  derniereDateSynchronisee: string | null;
  derniereExecution: string | null;
  statut: string;
  message: string | null;
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
}
