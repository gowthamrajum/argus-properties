import { bootstrapApplication } from '@angular/platform-browser';
import { App } from './app/app';
import { appConfig } from './app/app.config';

// Applied before first paint so a light-mode user never sees a dark flash.
if (localStorage.getItem('argus-theme') === 'light') {
  document.documentElement.classList.remove('dark');
}

bootstrapApplication(App, appConfig).catch((error: unknown) => console.error(error));
