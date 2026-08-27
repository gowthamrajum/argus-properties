import { provideHttpClient, withFetch } from '@angular/common/http';
import { type ApplicationConfig, provideZonelessChangeDetection } from '@angular/core';
import { provideRouter, withComponentInputBinding, withInMemoryScrolling } from '@angular/router';
import { routes } from './app.routes';

export const appConfig: ApplicationConfig = {
  providers: [
    // Nothing here needs zone.js: every async value in the app is already a signal.
    provideZonelessChangeDetection(),
    provideRouter(
      routes,
      // Binds route params and query params straight to component inputs, so a page reads its
      // filters as inputs rather than subscribing to ActivatedRoute.
      withComponentInputBinding(),
      // The tables of contents link to #fragments; without this the browser would not scroll.
      withInMemoryScrolling({ anchorScrolling: 'enabled', scrollPositionRestoration: 'enabled' }),
    ),
    provideHttpClient(withFetch()),
  ],
};
