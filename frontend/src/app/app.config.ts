import { registerLocaleData } from '@angular/common';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import localePt from '@angular/common/locales/pt';
import {
  ApplicationConfig,
  LOCALE_ID,
  isDevMode,
  provideBrowserGlobalErrorListeners,
} from '@angular/core';
import { MAT_FORM_FIELD_DEFAULT_OPTIONS } from '@angular/material/form-field';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { provideServiceWorker } from '@angular/service-worker';

import { routes } from './app.routes';
import { apiInterceptor } from './core/interceptors';

registerLocaleData(localePt);

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes, withComponentInputBinding()),
    provideHttpClient(withInterceptors([apiInterceptor])),
    { provide: LOCALE_ID, useValue: 'pt-BR' },
    { provide: MAT_FORM_FIELD_DEFAULT_OPTIONS, useValue: { appearance: 'outline' } },
    provideServiceWorker('ngsw-worker.js', {
      enabled: !isDevMode(),
      // Offline é requisito: registra já no primeiro acesso, sem esperar a app estabilizar.
      registrationStrategy: 'registerImmediately',
    }),
  ],
};
