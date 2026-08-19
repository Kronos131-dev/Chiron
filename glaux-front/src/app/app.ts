import { Component, inject, signal } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { PwaUpdateService } from './service/pwa-update.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet],
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class App {
  protected readonly title = signal('Noctua');

  private readonly pwaUpdate = inject(PwaUpdateService);

  constructor() {
    this.pwaUpdate.init();
  }
}
