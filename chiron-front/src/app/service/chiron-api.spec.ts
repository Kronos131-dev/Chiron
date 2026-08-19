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

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('updateProgrammesOrder', () => {
    it('sends PUT to /api/programmes/order with the ordered IDs as body', () => {
      service.updateProgrammesOrder('alice', [3, 1, 2]).subscribe();

      const req = httpMock.expectOne(`${environment.apiUrl}/programmes/order?username=alice`);
      expect(req.request.method).toBe('PUT');
      expect(req.request.body).toEqual([3, 1, 2]);
      req.flush(null);
    });

    it('URL-encodes the username param', () => {
      service.updateProgrammesOrder('alice bob', [1]).subscribe();

      const req = httpMock.expectOne(`${environment.apiUrl}/programmes/order?username=alice%20bob`);
      expect(req.request.method).toBe('PUT');
      req.flush(null);
    });
  });

  describe('getActivites', () => {
    it('defaults to 400 days with no source filter', () => {
      service.getActivites().subscribe();

      const req = httpMock.expectOne(`${environment.apiUrl}/sante/activites?jours=400`);
      expect(req.request.method).toBe('GET');
      req.flush([]);
    });

    it('forwards an explicit days count and source filter', () => {
      service.getActivites(30, 'CHIRON_MUSCU').subscribe();

      const req = httpMock.expectOne(`${environment.apiUrl}/sante/activites?jours=30&source=CHIRON_MUSCU`);
      expect(req.request.method).toBe('GET');
      req.flush([]);
    });
  });
});
