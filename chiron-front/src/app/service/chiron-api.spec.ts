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

      const req = httpMock.expectOne(
        `${environment.apiUrl}/sante/activites?jours=30&source=CHIRON_MUSCU`,
      );
      expect(req.request.method).toBe('GET');
      req.flush([]);
    });
  });

  describe('exports du programme', () => {
    const requete = {
      nom: 'Programme force',
      modeleIds: [5, 6],
      seances: [{ seanceId: 30, modeleId: 6 }],
    };

    it('liste les exports', () => {
      service.getExports().subscribe();

      const req = httpMock.expectOne(`${environment.apiUrl}/exports`);
      expect(req.request.method).toBe('GET');
      req.flush([]);
    });

    it('lit le détail d un export', () => {
      service.getExport(9).subscribe();

      const req = httpMock.expectOne(`${environment.apiUrl}/exports/9`);
      expect(req.request.method).toBe('GET');
      req.flush({});
    });

    it('crée un export avec la sélection en corps', () => {
      service.creerExport(requete).subscribe();

      const req = httpMock.expectOne(`${environment.apiUrl}/exports`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual(requete);
      req.flush({});
    });

    it('modifie la sélection d un export', () => {
      service.modifierExport(9, requete).subscribe();

      const req = httpMock.expectOne(`${environment.apiUrl}/exports/9`);
      expect(req.request.method).toBe('PUT');
      expect(req.request.body).toEqual(requete);
      req.flush({});
    });

    it('supprime un export', () => {
      service.supprimerExport(9).subscribe();

      const req = httpMock.expectOne(`${environment.apiUrl}/exports/9`);
      expect(req.request.method).toBe('DELETE');
      req.flush(null);
    });

    it('télécharge le classeur en blob', () => {
      service.telechargerExport(9).subscribe();

      const req = httpMock.expectOne(`${environment.apiUrl}/exports/9/telecharger`);
      expect(req.request.method).toBe('GET');
      expect(req.request.responseType).toBe('blob');
      req.flush(new Blob(['x']));
    });
  });
});
