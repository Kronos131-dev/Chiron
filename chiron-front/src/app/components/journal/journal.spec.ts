import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { Journal } from './journal';
import { AuthService } from '../../service/auth.service';
import { SanteActiviteDto } from '../../service/chiron-api';
import { environment } from '../../../environments/environment';

function flushJournalRequests(httpMock: HttpTestingController, activites: SanteActiviteDto[] = []) {
  httpMock.expectOne(`${environment.apiUrl}/journal/historique?username=alice`).flush([]);
  httpMock
    .expectOne(`${environment.apiUrl}/sante/activites?jours=400&source=CHIRON_MUSCU`)
    .flush(activites);
  httpMock.expectOne(`${environment.apiUrl}/profile/alice?requestUsername=alice`).flush({});
}

function buildActivite(overrides: Partial<SanteActiviteDto>): SanteActiviteDto {
  return {
    id: 1,
    source: 'CHIRON_MUSCU',
    typeActivite: 'MUSCULATION',
    startTime: '2026-08-18T18:00:00',
    endTime: '2026-08-18T19:15:00',
    calories: null,
    caloriesEstimees: false,
    fcMoyenne: null,
    fcMin: null,
    fcMax: null,
    minutesZoneBasse: null,
    minutesZoneBruleuse: null,
    minutesZoneCardio: null,
    minutesZonePic: null,
    minutesZoneActive: null,
    chargeCardio: null,
    seanceId: null,
    enrichissementEnCours: false,
    ...overrides,
  };
}

describe('Journal', () => {
  let component: Journal;
  let fixture: ComponentFixture<Journal>;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Journal],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: { getUsername: () => 'alice' } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(Journal);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('should create', () => {
    fixture.detectChanges();
    flushJournalRequests(httpMock);
    expect(component).toBeTruthy();
  });

  it('activiteMontre maps a watch activity to its linked séance by seanceId', () => {
    fixture.detectChanges();
    const activite = buildActivite({
      calories: 406,
      fcMoyenne: 124,
      minutesZoneBruleuse: 46,
      minutesZoneCardio: 15,
      minutesZonePic: 0,
      chargeCardio: 71,
      seanceId: 42,
    });
    flushJournalRequests(httpMock, [activite]);

    expect(component.activiteMontre(42)).toEqual(activite);
    expect(component.activiteMontre(99)).toBeUndefined();
  });

  it('activiteMontre ignores watch activities with no linked séance', () => {
    fixture.detectChanges();
    const activite = buildActivite({ seanceId: null });
    flushJournalRequests(httpMock, [activite]);

    expect(component.activiteMontre(1)).toBeUndefined();
  });

  it('zonesActivite exposes all four tracked heart-rate zones with their minute counts', () => {
    fixture.detectChanges();
    flushJournalRequests(httpMock);

    const activite = buildActivite({
      minutesZoneBasse: 14,
      minutesZoneBruleuse: 46,
      minutesZoneCardio: 15,
      minutesZonePic: 0,
    });

    const zones = component.zonesActivite(activite);
    expect(zones.map((z) => z.minutes)).toEqual([14, 46, 15, 0]);
    expect(component.totalMinutesZonesActivite(activite)).toBe(75);
    expect(component.hasZonesActivite(activite)).toBe(true);
  });

  it('hasZonesActivite is false when no zone has any minutes', () => {
    fixture.detectChanges();
    flushJournalRequests(httpMock);

    const activite = buildActivite({});
    expect(component.hasZonesActivite(activite)).toBe(false);
  });
});
