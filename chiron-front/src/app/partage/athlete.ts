import { Observable, catchError, map, of } from 'rxjs';
import { ChironApi } from '../service/chiron-api';
import { Athlete } from './carte';

export function athleteDe(chironApi: ChironApi, username: string): Observable<Athlete> {
  const anonyme: Athlete = { username, niveau: null, palier: null };
  return chironApi.getProfile(username, username).pipe(
    map((profil) => ({
      username,
      niveau: Number(profil?.performanceTierLevel) || null,
      palier: profil?.performanceTier ?? null,
    })),
    catchError(() => of(anonyme)),
  );
}
