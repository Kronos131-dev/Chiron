import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { Coeur } from './coeur';
import { environment } from '../../../environments/environment';

function flushCoeurRequests(httpMock: HttpTestingController, selectedDate: string) {
  httpMock.expectOne(`${environment.apiUrl}/sante/frequence-cardiaque?date=${selectedDate}`).flush([]);
  httpMock.expectOne(`${environment.apiUrl}/sante/jours?jours=14`).flush([]);
  httpMock.expectOne(`${environment.apiUrl}/sante/jours?jours=84`).flush([]);
  httpMock.expectOne(`${environment.apiUrl}/sante/cardio-hebdo?semaines=12`).flush([]);
  httpMock.expectOne(`${environment.apiUrl}/noctua/non-lus`).flush({ count: 0 });
}

describe('Coeur', () => {
  let component: Coeur;
  let fixture: ComponentFixture<Coeur>;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Coeur],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(Coeur);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('caps the y axis at 175 as a floor rather than a hard ceiling', () => {
    fixture.detectChanges();
    flushCoeurRequests(httpMock, component.selectedDate());

    expect(component.lineOptions?.scales?.['y']).toMatchObject({ suggestedMax: 175 });
  });

  it('exposes exactly the moderate/intense/maximal threshold plugin', () => {
    fixture.detectChanges();
    flushCoeurRequests(httpMock, component.selectedDate());

    expect(component.fcPlugins).toHaveLength(1);
    expect(component.fcPlugins[0].id).toBe('seuilsFc');
  });

  it('draws a dashed line and a label for each threshold within the current scale', () => {
    fixture.detectChanges();
    flushCoeurRequests(httpMock, component.selectedDate());

    const ctx = {
      save: vi.fn(),
      restore: vi.fn(),
      beginPath: vi.fn(),
      moveTo: vi.fn(),
      lineTo: vi.fn(),
      stroke: vi.fn(),
      fillText: vi.fn(),
      setLineDash: vi.fn(),
      strokeStyle: '',
      fillStyle: '',
      font: '',
      textAlign: '',
    };
    const scaleY = {
      min: 0,
      max: 175,
      getPixelForValue: (v: number) => 200 - v,
    };
    const chart = {
      ctx,
      chartArea: { left: 10, right: 300, top: 0, bottom: 200 },
      scales: { y: scaleY },
    };

    (component.fcPlugins[0].afterDatasetsDraw as (c: unknown) => void)(chart);

    expect(ctx.moveTo).toHaveBeenCalledTimes(3);
    expect(ctx.lineTo).toHaveBeenCalledTimes(3);
    expect(ctx.fillText).toHaveBeenCalledTimes(3);
    expect(ctx.moveTo).toHaveBeenCalledWith(10, expect.any(Number));
    expect(ctx.lineTo).toHaveBeenCalledWith(300, expect.any(Number));
  });

  it('skips a threshold that falls outside the current y-axis range', () => {
    fixture.detectChanges();
    flushCoeurRequests(httpMock, component.selectedDate());

    const ctx = {
      save: vi.fn(),
      restore: vi.fn(),
      beginPath: vi.fn(),
      moveTo: vi.fn(),
      lineTo: vi.fn(),
      stroke: vi.fn(),
      fillText: vi.fn(),
      setLineDash: vi.fn(),
      strokeStyle: '',
      fillStyle: '',
      font: '',
      textAlign: '',
    };
    // Échelle bornée à 120 : les seuils "intense" (139) et "maximale" (175) sortent du
    // cadre, seul "modérée" (110) reste dans la plage affichée.
    const scaleY = { min: 0, max: 120, getPixelForValue: (v: number) => 200 - v };
    const chart = { ctx, chartArea: { left: 10, right: 300, top: 0, bottom: 200 }, scales: { y: scaleY } };

    (component.fcPlugins[0].afterDatasetsDraw as (c: unknown) => void)(chart);

    expect(ctx.fillText).toHaveBeenCalledTimes(1);
  });
});
