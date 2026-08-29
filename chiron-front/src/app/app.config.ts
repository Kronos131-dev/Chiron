import { ApplicationConfig, LOCALE_ID, provideBrowserGlobalErrorListeners, isDevMode } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { registerLocaleData } from '@angular/common';
import localeFr from '@angular/common/locales/fr';
import localeEn from '@angular/common/locales/en';
import { provideCharts, withDefaultRegisterables } from 'ng2-charts';
import { provideServiceWorker } from '@angular/service-worker';
import { Capacitor } from '@capacitor/core';
import { authInterceptor } from './security/auth.interceptor';

import { routes } from './app.routes';

registerLocaleData(localeFr);
registerLocaleData(localeEn);

// Locale des dates/nombres alignée sur la langue persistée (chiron_lang). Le texte switche à
// chaud via I18nService ; le format date/nombre suit au chargement suivant.
const storedLang = (typeof localStorage !== 'undefined' && localStorage.getItem('chiron_lang')) || 'fr';
const localeId = storedLang === 'en' ? 'en-US' : 'fr-FR';

/**
 * Global application configuration object.
 * Defines the core providers for routing, HTTP client with interceptors,
 * and other third-party modules like ng2-charts.
 */
export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    { provide: LOCALE_ID, useValue: localeId },
    provideRouter(routes),
    provideHttpClient(withInterceptors([authInterceptor])),
    provideCharts(withDefaultRegisterables()),
    // PWA : enregistre le service worker (offline + installabilité WebAPK).
    // Désactivé en dev, et dans l'app native où PwaUpdateService bouclerait sur des
    // rechargements : les assets y sont déjà locaux, servis par la WebView.
    provideServiceWorker('ngsw-worker.js', {
      enabled: !isDevMode() && !Capacitor.isNativePlatform(),
      registrationStrategy: 'registerWhenStable:30000',
    })
  ],
};
