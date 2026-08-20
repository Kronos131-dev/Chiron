import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { ActiviteDetail } from './activite-detail';
import {
  NoctuaBriefingDto,
  SanteActiviteDetailDto,
  SanteActiviteDto,
} from '../../service/glaux-api';
import { environment } from '../../../environments/environment';

function buildActivite(overrides: Partial<SanteActiviteDto> = {}): SanteActiviteDto {
  return {
    id: 42,
    source: 'CHIRON_MUSCU',
    typeActivite: 'MUSCULATION',
    startTime: '2026-08-20T11:59:00',
    endTime: '2026-08-20T12:45:00',
    calories: 320,
    fcMoyenne: 128,
    fcMin: 90,
    fcMax: 150,
    minutesZoneBasse: 10,
    minutesZoneBruleuse: 20,
    minutesZoneCardio: 12,
    minutesZonePic: 4,
    minutesZoneActive: 36,
    chargeCardio: 61,
    seanceId: 7,
    enrichissementEnCours: false,
    ...overrides,
  };
}

function buildDetail(overrides: Partial<SanteActiviteDetailDto> = {}): SanteActiviteDetailDto {
  return {
    activite: buildActivite(),
    pointsFrequenceCardiaque: [
      { horodatage: '2026-08-20T12:00:00', fcMin: 100, fcMoyenne: 120, fcMax: 140 },
      { horodatage: '2026-08-20T12:30:00', fcMin: 110, fcMoyenne: 135, fcMax: 150 },
    ],
    seuils: { modere: 110, intense: 139, maximum: 175 },
    ...overrides,
  };
}

async function setup(id = '42') {
  await TestBed.configureTestingModule({
    imports: [ActiviteDetail],
    providers: [
      provideHttpClient(),
      provideHttpClientTesting(),
      provideRouter([]),
      { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ id }) } } },
    ],
  }).compileComponents();

  const fixture = TestBed.createComponent(ActiviteDetail);
  const httpMock = TestBed.inject(HttpTestingController);
  return { fixture, component: fixture.componentInstance, httpMock };
}

function flushNonLus(httpMock: HttpTestingController) {
  httpMock.expectOne(`${environment.apiUrl}/noctua/non-lus`).flush({ count: 0 });
}

describe('ActiviteDetail', () => {
  let httpMock: HttpTestingController;

  afterEach(() => httpMock.verify());

  it('loads the activity detail and its heart-rate points', async () => {
    const { fixture, component, httpMock: mock } = await setup();
    httpMock = mock;
    fixture.detectChanges();
    flushNonLus(httpMock);

    const detail = buildDetail();
    httpMock.expectOne(`${environment.apiUrl}/sante/activites/42`).flush(detail);
    httpMock.expectOne(`${environment.apiUrl}/noctua/briefings/par-activite/42`).flush(null);

    expect(component.detail()).toEqual(detail);
    expect(component.loading()).toBe(false);
    expect(component.notFound()).toBe(false);
  });

  it('flags notFound when the activity does not belong to the user or is unknown', async () => {
    const { fixture, component, httpMock: mock } = await setup('99');
    httpMock = mock;
    fixture.detectChanges();
    flushNonLus(httpMock);

    httpMock
      .expectOne(`${environment.apiUrl}/sante/activites/99`)
      .flush({ message: 'Activité introuvable' }, { status: 404, statusText: 'Not Found' });
    httpMock.expectOne(`${environment.apiUrl}/noctua/briefings/par-activite/99`).flush(null);

    expect(component.notFound()).toBe(true);
    expect(component.loading()).toBe(false);
  });

  it('keeps the briefing null when Noctua has none for this activity, without failing the page', async () => {
    const { fixture, component, httpMock: mock } = await setup();
    httpMock = mock;
    fixture.detectChanges();
    flushNonLus(httpMock);

    httpMock.expectOne(`${environment.apiUrl}/sante/activites/42`).flush(buildDetail());
    httpMock
      .expectOne(`${environment.apiUrl}/noctua/briefings/par-activite/42`)
      .flush({ message: 'Briefing introuvable' }, { status: 404, statusText: 'Not Found' });

    expect(component.briefing()).toBeNull();
  });

  it('ouvrirBriefing navigates to the Noctua briefing once one is loaded', async () => {
    const { fixture, component, httpMock: mock } = await setup();
    httpMock = mock;
    fixture.detectChanges();
    flushNonLus(httpMock);

    httpMock.expectOne(`${environment.apiUrl}/sante/activites/42`).flush(buildDetail());
    const briefing: NoctuaBriefingDto = {
      id: 3,
      type: 'ACTIVITE',
      dateReference: '2026-08-20',
      createdAt: '2026-08-20T12:45:00',
      lu: false,
      premierParagraphe: 'Séance dense, effort au-dessus de la moyenne.',
    };
    httpMock.expectOne(`${environment.apiUrl}/noctua/briefings/par-activite/42`).flush(briefing);

    const router = TestBed.inject(Router);
    const navigateSpy = vi.spyOn(router, 'navigate');

    component.ouvrirBriefing();

    expect(navigateSpy).toHaveBeenCalledWith(['/noctua', 3]);
  });

  it('retour navigates back to the activity list', async () => {
    const { fixture, component, httpMock: mock } = await setup();
    httpMock = mock;
    fixture.detectChanges();
    flushNonLus(httpMock);
    httpMock.expectOne(`${environment.apiUrl}/sante/activites/42`).flush(buildDetail());
    httpMock.expectOne(`${environment.apiUrl}/noctua/briefings/par-activite/42`).flush(null);

    const router = TestBed.inject(Router);
    const navigateSpy = vi.spyOn(router, 'navigate');

    component.retour();

    expect(navigateSpy).toHaveBeenCalledWith(['/activite']);
  });

  it('fcChart builds a 5-minute-stepped window scoped to [startTime, endTime]', async () => {
    const { fixture, component, httpMock: mock } = await setup();
    httpMock = mock;
    fixture.detectChanges();
    flushNonLus(httpMock);

    httpMock.expectOne(`${environment.apiUrl}/sante/activites/42`).flush(buildDetail());
    httpMock.expectOne(`${environment.apiUrl}/noctua/briefings/par-activite/42`).flush(null);

    const chart = component.fcChart();
    expect(chart.labels).toEqual([
      '11:55',
      '12:00',
      '12:05',
      '12:10',
      '12:15',
      '12:20',
      '12:25',
      '12:30',
      '12:35',
      '12:40',
      '12:45',
    ]);
    const [minDataset, maxDataset, avgDataset] = chart.datasets;
    expect(minDataset.data[1]).toBe(100);
    expect(maxDataset.data[1]).toBe(140);
    expect(avgDataset.data[1]).toBe(120);
    expect(avgDataset.data[0]).toBeNull();
  });

  it('zones and hasZones reflect the tracked heart-rate zones', async () => {
    const { fixture, component, httpMock: mock } = await setup();
    httpMock = mock;
    fixture.detectChanges();
    flushNonLus(httpMock);

    httpMock.expectOne(`${environment.apiUrl}/sante/activites/42`).flush(buildDetail());
    httpMock.expectOne(`${environment.apiUrl}/noctua/briefings/par-activite/42`).flush(null);

    expect(component.zones().map((z) => z.minutes)).toEqual([10, 20, 12, 4]);
    expect(component.totalMinutesZones()).toBe(46);
    expect(component.hasZones()).toBe(true);
  });

  it('duree formats the window as hours and minutes', async () => {
    const { fixture, component, httpMock: mock } = await setup();
    httpMock = mock;
    fixture.detectChanges();
    flushNonLus(httpMock);

    httpMock.expectOne(`${environment.apiUrl}/sante/activites/42`).flush(buildDetail());
    httpMock.expectOne(`${environment.apiUrl}/noctua/briefings/par-activite/42`).flush(null);

    expect(component.duree()).toBe('46min');
  });
});
