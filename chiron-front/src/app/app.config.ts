import { ApplicationConfig, LOCALE_ID, provideBrowserGlobalErrorListeners, isDevMode } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { registerLocaleData } from '@angular/common';
import localeFr from '@angular/common/locales/fr';
import { provideCharts, withDefaultRegisterables } from 'ng2-charts';
import { authInterceptor } from './security/auth.interceptor';

import { routes } from './app.routes';

registerLocaleData(localeFr);

/**
 * Global application configuration object.
 * Defines the core providers for routing, HTTP client with interceptors,
 * and other third-party modules like ng2-charts.
 */
export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    { provide: LOCALE_ID, useValue: 'fr-FR' },
    provideRouter(routes),
    provideHttpClient(withInterceptors([authInterceptor])),
    provideCharts(withDefaultRegisterables())
  ],
};
