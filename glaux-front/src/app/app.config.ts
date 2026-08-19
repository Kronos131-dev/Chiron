import {
  ApplicationConfig,
  LOCALE_ID,
  isDevMode,
  provideBrowserGlobalErrorListeners,
} from '@angular/core';
import { provideServiceWorker } from '@angular/service-worker';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { registerLocaleData } from '@angular/common';
import localeFr from '@angular/common/locales/fr';
import localeEn from '@angular/common/locales/en';
import { provideCharts, withDefaultRegisterables } from 'ng2-charts';
import { authInterceptor } from './security/auth.interceptor';

import { routes } from './app.routes';

registerLocaleData(localeFr);
registerLocaleData(localeEn);

const storedLang =
  (typeof localStorage !== 'undefined' && localStorage.getItem('chiron_lang')) || 'fr';
const localeId = storedLang === 'en' ? 'en-US' : 'fr-FR';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    { provide: LOCALE_ID, useValue: localeId },
    provideRouter(routes),
    provideHttpClient(withInterceptors([authInterceptor])),
    provideCharts(withDefaultRegisterables()),
    provideServiceWorker('ngsw-worker.js', {
      enabled: !isDevMode(),
      registrationStrategy: 'registerWhenStable:30000',
    }),
  ],
};
