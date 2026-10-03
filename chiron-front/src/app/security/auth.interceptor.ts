import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { AuthService } from '../service/auth.service';
import { environment } from '../../environments/environment';

/**
 * HTTP interceptor that automatically attaches the JWT Bearer token to outgoing requests
 * destined for the application's backend API.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const token = authService.getToken();

  if (token && req.url.includes(environment.apiUrl)) {
    const clonedReq = req.clone({
      headers: req.headers.set('Authorization', `Bearer ${token}`)
    });
    const estAuthentification = req.url.includes('/auth/');
    return next(clonedReq).pipe(
      catchError((erreur: unknown) => {
        // WHY: pour un jeton expiré ou illisible le serveur répond 403 et non 401 : Spring n'a pas
        // de point d'entrée configuré. Mais un 403 dit aussi « interdit » pour un utilisateur bien
        // connecté (profil privé, route admin). Il ne signifie une session perdue que si le jeton
        // est lui-même périmé ; sinon l'écran garde la main et affiche son erreur.
        const sessionPerdue =
          erreur instanceof HttpErrorResponse &&
          !estAuthentification &&
          authService.getToken() === token &&
          (erreur.status === 401 || (erreur.status === 403 && authService.jetonPerime(token)));
        if (sessionPerdue) authService.logout();
        return throwError(() => erreur);
      }),
    );
  }

  return next(req);
};
