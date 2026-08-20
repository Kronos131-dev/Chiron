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
    path: 'noctua',
    loadComponent: () => import('./components/noctua/noctua').then((m) => m.Noctua),
    canActivate: [authGuard],
  },
  {
    path: 'noctua/:id',
    loadComponent: () => import('./components/noctua/noctua').then((m) => m.Noctua),
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
  {
    path: 'activite/:id',
    loadComponent: () =>
      import('./components/activite-detail/activite-detail').then((m) => m.ActiviteDetail),
    canActivate: [authGuard],
  },
  { path: '**', redirectTo: '' },
];
