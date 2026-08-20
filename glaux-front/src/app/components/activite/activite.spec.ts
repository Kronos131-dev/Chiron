import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { Activite } from './activite';
import { SanteActiviteDto } from '../../service/glaux-api';
import { environment } from '../../../environments/environment';

function flushActiviteRequests(httpMock: HttpTestingController, activites: SanteActiviteDto[] = []) {
  httpMock.expectOne(`${environment.apiUrl}/sante/jours?jours=30`).flush([]);
  httpMock.expectOne(`${environment.apiUrl}/sante/activites?jours=90`).flush(activites);
  httpMock.expectOne(`${environment.apiUrl}/noctua/non-lus`).flush({ count: 0 });
}

function buildActivite(overrides: Partial<SanteActiviteDto>): SanteActiviteDto {
  return {
    id: 1,
    source: 'CHIRON_MUSCU',
    typeActivite: 'MUSCULATION',
    startTime: '2026-08-18T18:00:00',
    endTime: '2026-08-18T19:15:00',
    calories: null,
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

describe('Activite', () => {
  let component: Activite;
  let fixture: ComponentFixture<Activite>;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Activite],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(Activite);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('should create', () => {
    fixture.detectChanges();
    flushActiviteRequests(httpMock);
    expect(component).toBeTruthy();
  });

  it('loads recent watch activities on init', () => {
    fixture.detectChanges();
    const activite = buildActivite({ typeActivite: 'COURSE' });
    flushActiviteRequests(httpMock, [activite]);

    expect(component.activites()).toEqual([activite]);
    expect(component.activitesLoading()).toBe(false);
  });

  it('iconFor maps each activity type to a distinct Material Symbols icon', () => {
    fixture.detectChanges();
    flushActiviteRequests(httpMock);

    expect(component.iconFor('MUSCULATION')).toBe('fitness_center');
    expect(component.iconFor('MARCHE')).toBe('directions_walk');
    expect(component.iconFor('COURSE')).toBe('directions_run');
    expect(component.iconFor('VELO')).toBe('directions_bike');
    expect(component.iconFor('FOOTBALL')).toBe('sports_soccer');
    expect(component.iconFor('SPORT_AUTRE')).toBe('sports');
  });

  it('duree formats a sub-hour activity as minutes only', () => {
    fixture.detectChanges();
    flushActiviteRequests(httpMock);

    const activite = buildActivite({ startTime: '2026-08-18T18:00:00', endTime: '2026-08-18T18:45:00' });
    expect(component.duree(activite)).toBe('45min');
  });

  it('duree formats an hour-plus activity as h and padded minutes', () => {
    fixture.detectChanges();
    flushActiviteRequests(httpMock);

    const activite = buildActivite({ startTime: '2026-08-18T18:00:00', endTime: '2026-08-18T19:15:00' });
    expect(component.duree(activite)).toBe('1h15');
  });

  it('zonesActivite and hasZonesActivite reflect the tracked heart-rate zones', () => {
    fixture.detectChanges();
    flushActiviteRequests(httpMock);

    const withZones = buildActivite({
      minutesZoneBasse: 14,
      minutesZoneBruleuse: 46,
      minutesZoneCardio: 15,
      minutesZonePic: 0,
    });
    expect(component.zonesActivite(withZones).map((z) => z.minutes)).toEqual([14, 46, 15, 0]);
    expect(component.totalMinutesZonesActivite(withZones)).toBe(75);
    expect(component.hasZonesActivite(withZones)).toBe(true);

    const withoutZones = buildActivite({});
    expect(component.hasZonesActivite(withoutZones)).toBe(false);
  });
});
