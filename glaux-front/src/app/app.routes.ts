import { Routes } from '@angular/router';
import { authGuard } from './security/auth.guard';

export const routes: Routes = [
  { path: 'login', loadComponent: () => import('./components/login/login').then((m) => m.Login) },
  {
    path: '',
    loadComponent: () => import('./components/today/today').then((m) => m.Today),
    canActivate: [authGuard],
  },
  {
    path: 'coeur',
    loadComponent: () => import('./components/coeur/coeur').then((m) => m.Coeur),
    canActivate: [authGuard],
  },
  {
    path: 'sommeil',
    loadComponent: () => import('./components/sommeil/sommeil').then((m) => m.Sommeil),
    canActivate: [authGuard],
  },
  {
    path: 'activite',
    loadComponent: () => import('./components/activite/activite').then((m) => m.Activite),
    canActivate: [authGuard],
  },
  { path: '**', redirectTo: '' },
];
