import { bootstrapApplication } from '@angular/platform-browser';
import { appConfig } from './app/app.config';
import { App } from './app/app';

function consumeHandoffToken(): void {
  const match = location.hash.match(/#ctk=([^&]+)/);
  if (!match) return;
  localStorage.setItem('chiron_jwt', decodeURIComponent(match[1]));
  history.replaceState(null, '', location.pathname + location.search);
}

consumeHandoffToken();

bootstrapApplication(App, appConfig).catch((err) => console.error(err));
