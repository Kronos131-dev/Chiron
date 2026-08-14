# Spec skeletons

Copy the structure. Both patterns are taken from existing specs in `chiron-front/src/app/`.

## A `ChironApi` method

From `service/chiron-api.spec.ts`. Note `httpMock.verify()` in `afterEach` — an unflushed request
fails the test rather than passing silently.

```ts
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { ChironApi } from './chiron-api';
import { environment } from '../../environments/environment';

describe('ChironApi', () => {
  let service: ChironApi;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(ChironApi);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  describe('updateProgrammesOrder', () => {
    it('sends PUT to /api/programmes/order with the ordered IDs as body', () => {
      service.updateProgrammesOrder('alice', [3, 1, 2]).subscribe();

      const req = httpMock.expectOne(`${environment.apiUrl}/programmes/order?username=alice`);
      expect(req.request.method).toBe('PUT');
      expect(req.request.body).toEqual([3, 1, 2]);
      req.flush(null);
    });
  });
});
```

## A standalone component with the facade stubbed

The generated shape, extended into a real behaviour test:

```ts
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { of } from 'rxjs';

import { Journal } from './journal';
import { ChironApi } from '../../service/chiron-api';

describe('Journal', () => {
  let component: Journal;
  let fixture: ComponentFixture<Journal>;

  const chironApi = {
    getHistorique: vi.fn().mockReturnValue(of([
      { id: 1, titre: 'Push', startTime: '2026-03-02T10:00:00', weekNumber: 10, exercices: [] },
      { id: 2, titre: 'Pull', startTime: '2026-03-04T10:00:00', weekNumber: 10, exercices: [] },
      { id: 3, titre: 'Legs', startTime: '2026-03-09T10:00:00', weekNumber: 11, exercices: [] },
    ])),
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Journal],
      providers: [{ provide: ChironApi, useValue: chironApi }],
    }).compileComponents();

    fixture = TestBed.createComponent(Journal);
    component = fixture.componentInstance;
    await fixture.whenStable();
    fixture.detectChanges();
  });

  it('groups sessions by week', () => {
    expect(component.semaines().map((s) => s.numero)).toEqual([11, 10]);
    expect(component.semaines()[1].seances).toHaveLength(2);
  });
});
```

The stub returns `of(...)`, never a bare object — the component subscribes.

## A fixture factory

`session.spec.ts` uses one. Keep every required field of the interface in it, so a DTO change breaks
in one place instead of many:

```ts
import { ExerciceDefinitionDto } from '../../service/chiron-api';

function makeDef(id: number, nom: string): ExerciceDefinitionDto {
  return {
    id,
    nomFr: nom,
    nomEn: nom,
    imageUrl: null,
    imageUrl2: null,
    musclePrincipal: null,
    musclesSecondaires: [],
    typeEquipement: null,
    difficulte: null,
    descriptionFr: null,
    descriptionEn: null,
    cardioType: null,
  };
}
```

The `cardioType: null` line is exactly the one missing on `main` today, which is why the whole suite
fails to compile. When a DTO interface gains a required field, every factory building it must be
updated in the same change.

## A component using the `| t` pipe

`TranslatePipe` is standalone and must be imported by the test as well:

```ts
import { TranslatePipe } from '../../service/translate.pipe';

await TestBed.configureTestingModule({
  imports: [Profile, TranslatePipe],
  providers: [{ provide: ChironApi, useValue: chironApi }],
}).compileComponents();
```

## Asserting on the rendered DOM

```ts
it('shows the empty state when there is no session', () => {
  fixture.detectChanges();
  const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
  expect(text).toContain('Aucune séance');
});
```

Call `fixture.detectChanges()` after any signal change, or the DOM still holds the previous value.
