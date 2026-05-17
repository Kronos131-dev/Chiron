import { Routes } from '@angular/router';
import { Chat } from './components/chat/chat';
import { Journal } from './components/journal/journal';
import { Login } from './components/login/login';
import { authGuard } from './security/auth.guard';
import { Programme } from './components/programme/programme';
import { ProgrammeBuilder } from './components/programme-builder/programme-builder';
import { Session } from './components/session/session';
import { Profile } from './components/profile/profile';
import { Agora } from './components/agora/agora';
import { Tresor } from './components/tresor/tresor';
import { Bibliotheque } from './components/bibliotheque/bibliotheque';
import { Settings } from './components/settings/settings';
import { ResetPassword } from './components/reset-password/reset-password';

/**
 * Global routing configuration for the Angular application.
 * Defines navigation paths, their associated components, and applied security guards.
 */
export const routes: Routes = [
  { path: 'login', component: Login },

  {
    path: 'chat',
    component: Chat,
    canActivate: [authGuard]
  },
  {
    path: 'journal',
    component: Journal,
    canActivate: [authGuard]
  },
  {
    path: 'programme',
    component: Programme,
    canActivate: [authGuard]
  },
  {
    path: 'programme/new',
    component: ProgrammeBuilder,
    canActivate: [authGuard]
  },
  {
    path: 'programme/:id/edit',
    component: ProgrammeBuilder,
    canActivate: [authGuard]
  },
  {
    path: 'session',
    component: Session,
    canActivate: [authGuard]
  },
  {
    path: 'profile',
    component: Profile,
    canActivate: [authGuard]
  },
  {
    path: 'agora',
    component: Agora,
    canActivate: [authGuard]
  },
  {
    path: 'session/:id',
    component: Session,
    canActivate: [authGuard]
  },
  {
    path: 'profile/:id',
    component: Profile,
    canActivate: [authGuard]
  },
  {
    path: 'tresor',
    component: Tresor,
    canActivate: [authGuard]
  },
  {
    path: 'tresor/:id',
    component: Tresor,
    canActivate: [authGuard]
  },

  {
    path: 'bibliotheque',
    component: Bibliotheque,
    canActivate: [authGuard]
  },

  {
    path: 'settings',
    component: Settings,
    canActivate: [authGuard]
  },
  { path: 'reset-password', component: ResetPassword },

  { path: '', redirectTo: 'chat', pathMatch: 'full' },
  { path: '**', redirectTo: 'chat' }
];
