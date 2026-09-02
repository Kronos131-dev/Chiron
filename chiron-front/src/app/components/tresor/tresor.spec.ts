import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { Tresor } from './tresor';
import { ChironApi } from '../../service/chiron-api';
import { AuthService } from '../../service/auth.service';

describe('Tresor', () => {
  let component: Tresor;
  let fixture: ComponentFixture<Tresor>;
  let chironApi: { addPerformanceRecord: ReturnType<typeof vi.fn> } & Record<string, any>;

  const course5km = {
    exerciseType: 'COURSE_5KM',
    nom: '5 km',
    distanceKm: 5,
    tempsSecondes: 1500,
    ratioPerformance: 12,
    tierLevel: 4,
  };

  beforeEach(async () => {
    chironApi = {
      getPerformanceSummary: vi
        .fn()
        .mockReturnValue(of({ poidsCorps: 80, exercises: [course5km] })),
      addPerformanceRecord: vi.fn().mockReturnValue(of({ exercises: [] })),
      updateBodyweight: vi.fn().mockReturnValue(of({})),
    };

    await TestBed.configureTestingModule({
      imports: [Tresor],
      providers: [
        { provide: ChironApi, useValue: chironApi },
        { provide: AuthService, useValue: { getUsername: vi.fn().mockReturnValue('alice') } },
        { provide: Router, useValue: { navigateByUrl: vi.fn() } },
        {
          provide: ActivatedRoute,
          useValue: {
            paramMap: of(convertToParamMap({})),
            snapshot: { paramMap: convertToParamMap({}) },
          },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(Tresor);
    component = fixture.componentInstance;
    component.ngOnInit();
  });

  it('reconnaît les deux épreuves de course', () => {
    expect(component.isCourse('COURSE_5KM')).toBe(true);
    expect(component.isCourse('COURSE_10KM')).toBe(true);
    expect(component.isCourse('SQUAT')).toBe(false);
  });

  it('ouvre le chrono existant sur les molettes minutes et secondes', () => {
    component.openModal({ ...course5km, tempsSecondes: 1523 });

    expect(component.minutesDrum.value()).toBe(25);
    expect(component.secondesDrum.value()).toBe(23);
    expect(component.tempsSaisiSecondes()).toBe(1523);
  });

  it('calcule la vitesse et le palier depuis le chrono saisi', () => {
    component.openModal(course5km);
    component.minutesDrum.set(25);
    component.secondesDrum.set(0);

    expect(component.previewVitesse()).toBe(12);
    expect(component.previewAllure()).toBe('5:00');
    expect(component.previewTier().name).toBe('Myrmidon');
  });

  it('envoie un chrono, jamais une charge, pour une course', () => {
    component.openModal(course5km);
    component.minutesDrum.set(24);
    component.secondesDrum.set(30);

    component.submitRecord();

    expect(chironApi.addPerformanceRecord).toHaveBeenCalledWith('alice', {
      exerciseType: 'COURSE_5KM',
      tempsSecondes: 1470,
    });
  });

  it('envoie une charge et des reps pour un exercice de barre', () => {
    component.openModal({ exerciseType: 'SQUAT', poids: 100, nombreReps: 5, tierLevel: 3 });
    component.poidsDrum.set(120);
    component.repsDrum.set(3);

    component.submitRecord();

    expect(chironApi.addPerformanceRecord).toHaveBeenCalledWith('alice', {
      exerciseType: 'SQUAT',
      poids: 120,
      nombreReps: 3,
    });
  });

  it('exprime en secondes à gagner ce qui manque au palier suivant', () => {
    const progress = component.getProgress(course5km);

    expect(progress?.nextTier).toBe('Spartiate');
    expect(progress?.manque).toBe('1:00');
  });

  it("exprime en ratio ce qui manque au palier suivant d'une barre", () => {
    const progress = component.getProgress({
      exerciseType: 'SQUAT',
      ratioPerformance: 1.4,
      tierLevel: 4,
    });

    expect(progress?.manque).toBe('0.1×');
  });
});
