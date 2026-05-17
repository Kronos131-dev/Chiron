import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { ProgrammeBuilder } from './programme-builder';
import { ChironApi, ExerciceDefinitionDto } from '../../service/chiron-api';
import { AuthService } from '../../service/auth.service';

describe('ProgrammeBuilder', () => {
  let component: ProgrammeBuilder;
  let fixture: ComponentFixture<ProgrammeBuilder>;
  let chironApi: {
    getProgrammeById: ReturnType<typeof vi.fn>;
    sauvegarderProgramme: ReturnType<typeof vi.fn>;
    searchExercices: ReturnType<typeof vi.fn>;
  };
  let auth: { getUsername: ReturnType<typeof vi.fn> };
  let router: { navigate: ReturnType<typeof vi.fn> };

  function makeDef(id: number, nom: string): ExerciceDefinitionDto {
    return {
      id, nomFr: nom, nomEn: nom, imageUrl: null, imageUrl2: null,
      musclePrincipal: null, musclesSecondaires: [], typeEquipement: null,
      difficulte: null, descriptionFr: null, descriptionEn: null,
    };
  }

  async function bootWith(params: Record<string, string> = {}, queryParams: Record<string, string> = {}) {
    chironApi = {
      getProgrammeById:     vi.fn().mockReturnValue(of({ titre: 'Push', modele: false, exercices: [], utilisateur: { username: 'alice' } })),
      sauvegarderProgramme: vi.fn().mockReturnValue(of('Program saved with ID: 99')),
      searchExercices:      vi.fn().mockReturnValue(of([])),
    };
    auth   = { getUsername: vi.fn().mockReturnValue('alice') };
    router = { navigate: vi.fn() };
    const route = {
      paramMap:      of(convertToParamMap(params)),
      queryParamMap: of(convertToParamMap(queryParams)),
    };

    await TestBed.configureTestingModule({
      imports: [ProgrammeBuilder],
      providers: [
        { provide: ChironApi, useValue: chironApi },
        { provide: AuthService, useValue: auth },
        { provide: Router, useValue: router },
        { provide: ActivatedRoute, useValue: route },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ProgrammeBuilder);
    component = fixture.componentInstance;
    component.ngOnInit();
  }

  describe('new programme mode', () => {
    beforeEach(() => bootWith({}, {}));

    it('starts empty with the default title', () => {
      expect(component.exercices()).toEqual([]);
      expect(component.titre()).toBe('Nouveau programme');
      expect(component.programmeId).toBeNull();
      expect(component.isCoachMode()).toBe(false);
    });

    it('adds an exercise from the library when a card is tapped', () => {
      component.addExerciceFromDefinition(makeDef(7, 'Bench Press'));
      expect(component.exercices()).toHaveLength(1);
      expect(component.exercices()[0].nom).toBe('Bench Press');
      expect(component.exercices()[0].definitionId).toBe(7);
      expect(component.addedExercises()).toHaveLength(1);
    });

    it('tracks every library pick in the picker session list', () => {
      component.openPicker();
      component.addExerciceFromDefinition(makeDef(1, 'A'));
      component.addExerciceFromDefinition(makeDef(2, 'B'));
      component.addExerciceFromDefinition(makeDef(3, 'C'));
      expect(component.addedExercises().map(e => e.nom)).toEqual(['A', 'B', 'C']);
    });

    it('removeAddedFromPicker drops the exo from both the programme and the picker list', () => {
      component.openPicker();
      component.addExerciceFromDefinition(makeDef(1, 'A'));
      component.addExerciceFromDefinition(makeDef(2, 'B'));
      const toRemove = component.addedExercises()[0];

      component.removeAddedFromPicker(toRemove);

      expect(component.exercices().map(e => e.nom)).toEqual(['B']);
      expect(component.addedExercises().map(e => e.nom)).toEqual(['B']);
    });

    it('adds a custom (free-text) exercise and closes the picker', () => {
      component.openPicker();
      component.addCustomExercice();
      expect(component.exercices()).toHaveLength(1);
      expect(component.exercices()[0].definitionId).toBeUndefined();
      expect(component.pickerOpen()).toBe(false);
    });

    it('closePicker clears the picker session list but keeps the programme intact', () => {
      component.openPicker();
      component.addExerciceFromDefinition(makeDef(1, 'A'));

      component.closePicker();

      expect(component.pickerOpen()).toBe(false);
      expect(component.addedExercises()).toHaveLength(0);
      // The exercise stays in the programme; only the picker session list resets.
      expect(component.exercices()).toHaveLength(1);
    });

    it('save posts with id=null and stores the returned id', () => {
      component.titre.set('Push Day');
      component.addExerciceFromDefinition(makeDef(5, 'Bench'));

      component.save();

      expect(chironApi.sauvegarderProgramme).toHaveBeenCalledWith(
        'alice',
        expect.objectContaining({ id: null, titre: 'Push Day' }),
        undefined,
      );
      expect(component.programmeId).toBe('99');
    });
  });

  describe('coach mode (create for athlete)', () => {
    beforeEach(() => bootWith({}, { asUser: 'bob' }));

    it('flags coach mode and exposes the target username', () => {
      expect(component.isCoachMode()).toBe(true);
      expect(component.targetUsername()).toBe('bob');
    });

    it('save passes forUsername so the backend assigns the programme to the athlete', () => {
      component.titre.set('Coach Plan');
      component.save();

      expect(chironApi.sauvegarderProgramme).toHaveBeenCalledWith(
        'alice',                           // requester (logged-in coach)
        expect.objectContaining({ titre: 'Coach Plan' }),
        'bob',                             // forUsername (athlete)
      );
    });
  });

  describe('edit existing programme', () => {
    beforeEach(() => bootWith({ id: '42' }, {}));

    it('loads the existing programme by id', () => {
      expect(chironApi.getProgrammeById).toHaveBeenCalledWith('alice', '42');
      expect(component.programmeId).toBe('42');
    });

    it('save posts with the existing id (update)', () => {
      component.save();
      // Loading the programme sets targetUsername to the owner (alice in this test),
      // so forUsername is passed but the backend ignores it on update.
      expect(chironApi.sauvegarderProgramme).toHaveBeenCalledWith(
        'alice',
        expect.objectContaining({ id: 42 }),
        'alice',
      );
    });

    it('flashes an error status if the save fails', () => {
      chironApi.sauvegarderProgramme.mockReturnValueOnce(throwError(() => new Error('boom')));
      component.save();
      expect(component.saveStatus()).toContain('Erreur');
    });
  });

  describe('drag reorder', () => {
    function fakeDragEvent(): any {
      return { preventDefault: () => {}, dataTransfer: null };
    }

    beforeEach(async () => {
      await bootWith({}, {});
      component.addExerciceFromDefinition(makeDef(1, 'A'));
      component.addExerciceFromDefinition(makeDef(2, 'B'));
      component.addExerciceFromDefinition(makeDef(3, 'C'));
    });

    it('reorders the local list on drop', () => {
      component.onExoDragStart(fakeDragEvent(), 0, {} as HTMLElement);
      component.onExoDragOver(fakeDragEvent(), 2);
      component.onExoDrop(fakeDragEvent(), 2);

      expect(component.exercices().map(e => e.nom)).toEqual(['B', 'C', 'A']);
    });
  });
});
