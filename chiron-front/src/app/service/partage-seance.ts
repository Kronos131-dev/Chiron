import { Injectable, inject } from '@angular/core';
import { Observable, catchError, forkJoin, map, of } from 'rxjs';
import { AuthService } from './auth.service';
import { ChironApi, CourseTraceDto } from './chiron-api';
import { I18nService } from './i18n.service';
import { ExerciceBrut } from '../partage/bilan';
import { athleteDe } from '../partage/athlete';
import {
  ActiviteMontre,
  Athlete,
  CarteSeance,
  SeanceBrute,
  Traducteur,
  carteDeSeance,
  carteDeSortie,
  carteDeWod,
} from '../partage/carte';

@Injectable({ providedIn: 'root' })
export class PartageSeance {
  private chironApi = inject(ChironApi);
  private auth = inject(AuthService);
  private i18n = inject(I18nService);

  private readonly t: Traducteur = (cle, params) => this.i18n.t(cle, params);

  preparer(
    seance: SeanceBrute,
    activite: ActiviteMontre | null = null,
    tracesConnues: ReadonlyMap<number, CourseTraceDto> = new Map(),
  ): Observable<CarteSeance> {
    const id = this.idDeLaTrace(seance);
    const trace$ = id === null ? of(null) : this.traceOuChargee(id, tracesConnues);
    return forkJoin({ athlete: this.athlete(), trace: trace$ }).pipe(
      map(({ athlete, trace }) =>
        carteDeSeance({
          seance,
          traces: id !== null && trace ? new Map([[id, trace]]) : new Map(),
          activite,
          athlete,
          t: this.t,
          langue: this.i18n.lang(),
        }),
      ),
    );
  }

  preparerSortie(trace: CourseTraceDto, nom: string): Observable<CarteSeance> {
    return this.athlete().pipe(
      map((athlete) => carteDeSortie(trace, nom, new Date(), athlete, this.t, this.i18n.lang())),
    );
  }

  preparerWod(
    exercice: ExerciceBrut,
    tours: number,
    record: number | null,
    recordBattu: boolean,
  ): Observable<CarteSeance> {
    return this.athlete().pipe(
      map((athlete) =>
        carteDeWod(exercice, tours, record, recordBattu, new Date(), athlete, this.t),
      ),
    );
  }

  private athlete(): Observable<Athlete> {
    const username = this.auth.getUsername();
    if (!username) return of({ username: '', niveau: null, palier: null });
    return athleteDe(this.chironApi, username);
  }

  private idDeLaTrace(seance: SeanceBrute): number | null {
    const exercices = seance.exercices ?? [];
    if (exercices.length !== 1) return null;
    return exercices[0].series?.[0]?.courseTraceId ?? null;
  }

  private traceOuChargee(
    id: number,
    connues: ReadonlyMap<number, CourseTraceDto>,
  ): Observable<CourseTraceDto | null> {
    const deja = connues.get(id);
    if (deja) return of(deja);
    return this.chironApi.getTraceCourse(id).pipe(catchError(() => of(null)));
  }
}
