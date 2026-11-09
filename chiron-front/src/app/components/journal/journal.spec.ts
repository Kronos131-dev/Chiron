import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { Journal } from './journal';
import { AuthService } from '../../service/auth.service';
import { ExerciceDefinitionDto, SanteActiviteDto } from '../../service/chiron-api';
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

  describe('modifier une séance enregistrée', () => {
    const clic = new Event('click');

    function seanceEnregistree(): any {
      return {
        id: 7,
        titre: 'Push',
        note: 'Bonne forme',
        weekNumber: 45,
        startTime: '2026-11-03T18:00:00',
        endTime: '2026-11-03T19:10:00',
        exercices: [
          {
            id: 11,
            nom: 'Curl',
            commentaire: 'Coude sensible',
            unilateral: true,
            blockId: 5,
            blockType: 'SUPERSET',
            series: [
              {
                poids: 12,
                reps: 10,
                commentaire: 'dernière difficile',
                degressifs: [{ poids: 8, reps: 6 }],
              },
            ],
          },
          {
            id: 12,
            nom: 'Sortie',
            exerciceDefinitionId: 90,
            cardioType: 'COURSE_EXTERIEUR',
            series: [
              {
                poids: 0,
                reps: 0,
                dureeMin: 30,
                distanceM: 5000,
                allureKmh: 10,
                pentePct: null,
                courseTraceId: 321,
                degressifs: [],
              },
            ],
          },
        ],
      };
    }

    function ouvrirEdition(): void {
      fixture.detectChanges();
      flushJournalRequests(httpMock);
      component.startEdit(seanceEnregistree(), clic);
    }

    function enregistrer(): any {
      component.saveEdit(clic);
      const requete = httpMock.expectOne(`${environment.apiUrl}/programmes?username=alice`);
      return requete.request.body;
    }

    function definition(): ExerciceDefinitionDto {
      return {
        id: 40,
        nomFr: 'Bench',
        nomEn: 'Bench',
        cardioType: null,
        wodType: null,
      } as unknown as ExerciceDefinitionDto;
    }

    it('keeps everything the edit screen does not touch', () => {
      ouvrirEdition();

      const envoye = enregistrer();

      expect(envoye.note).toBe('Bonne forme');
      expect(envoye.endTime).toBe('2026-11-03T19:10:00');
      expect(envoye.exercices[0]).toMatchObject({
        unilateral: true,
        blockId: 5,
        blockType: 'SUPERSET',
      });
      expect(envoye.exercices[0].series[0]).toMatchObject({
        commentaire: 'dernière difficile',
        degressifs: [{ poids: 8, reps: 6 }],
      });
      expect(envoye.exercices[1].series[0]).toMatchObject({
        dureeMin: 30,
        distanceM: 5000,
        allureKmh: 10,
        courseTraceId: 321,
      });
    });

    it('renders every note, exercise card and the add button, without the live-session tools', () => {
      fixture.detectChanges();
      const seance = seanceEnregistree();
      httpMock.expectOne(`${environment.apiUrl}/journal/historique?username=alice`).flush([seance]);
      httpMock
        .expectOne(`${environment.apiUrl}/sante/activites?jours=400&source=CHIRON_MUSCU`)
        .flush([]);
      httpMock.expectOne(`${environment.apiUrl}/profile/alice?requestUsername=alice`).flush({});

      component.startEdit(seance, clic);
      fixture.detectChanges();
      httpMock.expectOne(`${environment.apiUrl}/exercices/90`).flush({ id: 90 });
      const page: HTMLElement = fixture.nativeElement;

      expect(page.querySelectorAll('app-exercice-card').length).toBe(2);
      expect(page.querySelectorAll('app-champ-note textarea').length).toBe(3);
      expect(page.querySelector('.drag-handle')).toBeNull();
      expect(page.querySelector('input[type="text"]')).not.toBeNull();
    });

    it('saves a rewritten title, session note and exercise note', () => {
      ouvrirEdition();
      const brouillon = component.editingDraft()!;

      brouillon.titre = 'Push lourd';
      brouillon.note = '';
      brouillon.exercices[0].commentaire = '';
      const envoye = enregistrer();

      expect(envoye.titre).toBe('Push lourd');
      expect(envoye.note).toBeNull();
      expect(envoye.exercices[0].commentaire).toBe('');
    });

    it('adds an exercise from the library and removes another', () => {
      ouvrirEdition();

      component.ajouterExerciceDepuisBibliotheque(definition());
      component.retirerExercice(11);
      const envoye = enregistrer();

      expect(envoye.exercices.map((e: any) => e.nom)).toEqual(['Sortie', 'Bench']);
      expect(envoye.exercices[1].exerciceDefinitionId).toBe(40);
    });

    it('lets a library pick be undone from the picker selection', () => {
      ouvrirEdition();

      component.ajouterExerciceDepuisBibliotheque(definition());
      const ajoute = component.exercicesAjoutes()[0];
      component.retirerExercice(ajoute.id);

      expect(component.exercicesAjoutes()).toEqual([]);
      expect(component.editingDraft()!.exercices.map((e) => e.id)).toEqual([11, 12]);
    });

    it('does not touch the journal until the edit is saved', () => {
      ouvrirEdition();

      component.retirerExercice(11);
      component.cancelEdit();

      expect(component.editingDraft()).toBeNull();
      expect(component.editingSeanceId()).toBeNull();
    });

    it('spends the Android back button on the picker, then on the edit, then lets it through', () => {
      ouvrirEdition();
      component.ouvrirSelecteur();

      expect(component.consommerRetour()).toBe(true);
      expect(component.selecteurOuvert()).toBe(false);
      expect(component.editingSeanceId()).toBe(7);

      expect(component.consommerRetour()).toBe(true);
      expect(component.editingSeanceId()).toBeNull();

      expect(component.consommerRetour()).toBe(false);
    });
  });
});
