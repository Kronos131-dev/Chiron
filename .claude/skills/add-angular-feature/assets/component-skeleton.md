# Component skeletons

Copy the structure. Taken from `components/fitbit-dashboard/` and `app.routes.ts`.

## The component class

```ts
import { Component, OnInit, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { ChironApi, FitbitDashboard as FitbitDashboardData } from '../../service/chiron-api';
import { HeaderComponent } from '../shared/header/header';
import { TranslatePipe } from '../../service/translate.pipe';

@Component({
  selector: 'app-fitbit-dashboard',
  standalone: true,
  imports: [CommonModule, HeaderComponent, TranslatePipe],
  templateUrl: './fitbit-dashboard.html',
  styleUrls: ['./fitbit-dashboard.css'],
})
export class FitbitDashboard implements OnInit {

  dashboard = signal<FitbitDashboardData | null>(null);
  isLoading = signal(true);
  error = signal<string | null>(null);

  totalPas = computed(() => this.dashboard()?.jours.reduce((sum, j) => sum + j.pas, 0) ?? 0);

  constructor(private chironApi: ChironApi, private router: Router) {}

  ngOnInit() {
    this.load();
  }

  load() {
    this.isLoading.set(true);
    this.error.set(null);
    this.chironApi.getFitbitDashboard(7).subscribe({
      next: (d) => {
        this.dashboard.set(d);
        this.isLoading.set(false);
      },
      error: () => {
        this.error.set('fitbit.erreur_chargement');
        this.isLoading.set(false);
      },
    });
  }
}
```

Three signals — data, loading, error — are the established shape. The error signal holds an **i18n
key**, not a sentence, so the template translates it.

## The template

```html
<app-header></app-header>

<main class="min-h-screen bg-background text-on-background p-4">
  <h1 class="text-2xl font-bold mb-4">{{ 'fitbit.titre' | t }}</h1>

  @if (isLoading()) {
    <p class="text-on-surface-variant">{{ 'common.chargement' | t }}</p>
  } @else if (error()) {
    <p class="text-secondary">{{ error()! | t }}</p>
  } @else {
    <p class="text-on-surface-variant">
      {{ 'fitbit.total_pas' | t: { pas: totalPas() } }}
    </p>
  }
</main>
```

Every string goes through `| t`. Colours come from the `@theme` tokens in `src/styles.css`
(`bg-background`, `text-on-surface-variant`, `text-secondary`), never raw hex.

## The facade method

`service/chiron-api.ts`:

```ts
export interface FitbitDashboard {
  jours: { date: string; pas: number; sommeilMinutes: number }[];
}

  getFitbitDashboard(days: number): Observable<FitbitDashboard> {
    return this.http.get<FitbitDashboard>(`${this.apiUrl}/fitbit/dashboard`, {
      params: { days },
    });
  }
```

The interface mirrors the backend DTO field for field. `LocalDate` and `LocalDateTime` arrive as ISO
strings.

## The route

Eager, for a core screen:

```ts
  {
    path: 'journal',
    component: Journal,
    canActivate: [authGuard],
  },
```

Lazy, for a heavy one — the form used by `exercice/:id`, `fitbit` and `statistics`:

```ts
  {
    path: 'fitbit',
    loadComponent: () =>
      import('./components/fitbit-dashboard/fitbit-dashboard').then((m) => m.FitbitDashboard),
    canActivate: [authGuard],
  },
```

`canActivate: [authGuard]` on every route except `login` and `reset-password`.

## The theme tokens

Declared in `src/styles.css` under `@theme`, and usable as Tailwind classes:

| Token | Value | Class |
|-------|-------|-------|
| `--color-background` | `#020617` | `bg-background` |
| `--color-surface` | `#020617` | `bg-surface` |
| `--color-primary-container` | `#001a4d` | `bg-primary-container` |
| `--color-secondary` | `#ffb779` | `text-secondary` |
| `--color-on-background` | `#f8fafc` | `text-on-background` |
| `--color-on-surface-variant` | `#94a3b8` | `text-on-surface-variant` |
| `--color-outline` | `#8e909c` | `border-outline` |
| `--color-outline-variant` | `#334155` | `border-outline-variant` |

Use these rather than inventing a colour; the palette is what makes the screens look like one app.
