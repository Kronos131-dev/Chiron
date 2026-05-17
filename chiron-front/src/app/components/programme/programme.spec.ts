import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { Programme } from './programme';
import { ChironApi } from '../../service/chiron-api';
import { AuthService } from '../../service/auth.service';

describe('Programme', () => {
  let component: Programme;
  let fixture: ComponentFixture<Programme>;
  let chironApi: { getProgrammes: ReturnType<typeof vi.fn>; deleteProgramme: ReturnType<typeof vi.fn>; updateProgrammesOrder: ReturnType<typeof vi.fn>; };
  let auth: { getUsername: ReturnType<typeof vi.fn>; };
  let router: { navigate: ReturnType<typeof vi.fn>; };

  function fakeDragEvent(): any {
    return { preventDefault: () => {}, dataTransfer: null };
  }

  beforeEach(async () => {
    chironApi = {
      getProgrammes: vi.fn().mockReturnValue(of([
        { id: 1, titre: 'A', exercices: [] },
        { id: 2, titre: 'B', exercices: [] },
        { id: 3, titre: 'C', exercices: [] },
      ])),
      deleteProgramme: vi.fn(),
      updateProgrammesOrder: vi.fn().mockReturnValue(of(null)),
    };
    auth = { getUsername: vi.fn().mockReturnValue('alice') };
    router = { navigate: vi.fn() };

    await TestBed.configureTestingModule({
      imports: [Programme],
      providers: [
        { provide: ChironApi, useValue: chironApi },
        { provide: AuthService, useValue: auth },
        { provide: Router, useValue: router },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(Programme);
    component = fixture.componentInstance;
    component.ngOnInit();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('loads programmes on init', () => {
    expect(chironApi.getProgrammes).toHaveBeenCalledWith('alice');
    expect(component.routines().map(r => r.id)).toEqual(['1', '2', '3']);
  });

  describe('navigation', () => {
    it('commencerRoutine opens the execution Session view', () => {
      component.commencerRoutine('42');
      expect(router.navigate).toHaveBeenCalledWith(['/session', '42']);
    });

    it('editerRoutine opens the ProgrammeBuilder in edit mode', () => {
      component.editerRoutine('42');
      expect(router.navigate).toHaveBeenCalledWith(['/programme', '42', 'edit']);
    });

    it('ajouterRoutine opens the ProgrammeBuilder in create mode', () => {
      component.ajouterRoutine();
      expect(router.navigate).toHaveBeenCalledWith(['/programme', 'new']);
    });
  });

  describe('drag reorder', () => {
    it('persists the new order via the API after a drop', () => {
      component.onDragStart(fakeDragEvent(), 0, {} as HTMLElement);
      component.onDragOver(fakeDragEvent(), 2);
      component.onDrop(fakeDragEvent(), 2);

      expect(component.routines().map(r => r.id)).toEqual(['2', '3', '1']);
      expect(chironApi.updateProgrammesOrder).toHaveBeenCalledWith('alice', [2, 3, 1]);
    });

    it('does not call the API when source and target indices are equal', () => {
      component.onDragStart(fakeDragEvent(), 1, {} as HTMLElement);
      component.onDragOver(fakeDragEvent(), 1);
      component.onDrop(fakeDragEvent(), 1);

      expect(chironApi.updateProgrammesOrder).not.toHaveBeenCalled();
      expect(component.routines().map(r => r.id)).toEqual(['1', '2', '3']);
    });

    it('rolls back the local order if the API call fails', () => {
      chironApi.updateProgrammesOrder.mockReturnValue(throwError(() => new Error('boom')));

      component.onDragStart(fakeDragEvent(), 0, {} as HTMLElement);
      component.onDragOver(fakeDragEvent(), 2);
      component.onDrop(fakeDragEvent(), 2);

      expect(chironApi.updateProgrammesOrder).toHaveBeenCalled();
      expect(component.routines().map(r => r.id)).toEqual(['1', '2', '3']);
    });
  });
});
