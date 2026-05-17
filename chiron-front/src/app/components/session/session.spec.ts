import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { Session, ExerciceForm } from './session';
import { ChironApi } from '../../service/chiron-api';
import { AuthService } from '../../service/auth.service';

describe('Session', () => {
  let component: Session;
  let fixture: ComponentFixture<Session>;

  function fakeDragEvent(): any {
    return { preventDefault: () => {}, dataTransfer: null };
  }

  function makeExo(id: string, nom: string): ExerciceForm {
    return { id, nom, series: [{ id: `s-${id}`, poids: null, reps: null, degressifs: [] }] };
  }

  beforeEach(async () => {
    const chironApi = {
      getProgrammeById:     vi.fn().mockReturnValue(of({ titre: 'T', modele: false, exercices: [] })),
      sauvegarderProgramme: vi.fn().mockReturnValue(of('')),
      searchExercices:      vi.fn().mockReturnValue(of([])),
    };
    const auth   = { getUsername: vi.fn().mockReturnValue('alice') };
    const router = { navigate: vi.fn() };
    const route  = {
      paramMap:    of(convertToParamMap({})),
      queryParams: of({}),
    };

    await TestBed.configureTestingModule({
      imports: [Session],
      providers: [
        { provide: ChironApi, useValue: chironApi },
        { provide: AuthService, useValue: auth },
        { provide: Router, useValue: router },
        { provide: ActivatedRoute, useValue: route },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(Session);
    component = fixture.componentInstance;
    component.ngOnInit();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  describe('exercise drag reorder', () => {
    beforeEach(() => {
      component.exercices.set([makeExo('a', 'Bench'), makeExo('b', 'Dips'), makeExo('c', 'OHP')]);
    });

    it('reorders the local list on drop', () => {
      component.onExoDragStart(fakeDragEvent(), 0, {} as HTMLElement);
      component.onExoDragOver(fakeDragEvent(), 2);
      component.onExoDrop(fakeDragEvent(), 2);

      expect(component.exercices().map(e => e.nom)).toEqual(['Dips', 'OHP', 'Bench']);
    });

    it('is a no-op when source and target indices are equal', () => {
      component.onExoDragStart(fakeDragEvent(), 1, {} as HTMLElement);
      component.onExoDragOver(fakeDragEvent(), 1);
      component.onExoDrop(fakeDragEvent(), 1);

      expect(component.exercices().map(e => e.nom)).toEqual(['Bench', 'Dips', 'OHP']);
    });

    it('does not reorder when read-only', () => {
      component.isReadonly.set(true);

      component.onExoDragStart(fakeDragEvent(), 0, {} as HTMLElement);
      component.onExoDragOver(fakeDragEvent(), 2);
      component.onExoDrop(fakeDragEvent(), 2);

      expect(component.exercices().map(e => e.nom)).toEqual(['Bench', 'Dips', 'OHP']);
    });

    it('clears drag visual state after dragend', () => {
      component.onExoDragStart(fakeDragEvent(), 0, {} as HTMLElement);
      component.onExoDragOver(fakeDragEvent(), 2);
      component.onExoDragEnd();

      expect(component.dragFromIdx()).toBe(-1);
      expect(component.dragOverIdx()).toBe(-1);
    });
  });
});
