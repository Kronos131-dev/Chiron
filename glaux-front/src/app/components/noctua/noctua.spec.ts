import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { Noctua } from './noctua';
import { NoctuaBriefingDetailDto, NoctuaBriefingDto } from '../../service/glaux-api';
import { environment } from '../../../environments/environment';

function buildBriefing(overrides: Partial<NoctuaBriefingDto> = {}): NoctuaBriefingDto {
  return {
    id: 3,
    type: 'REVEIL',
    dateReference: '2026-08-20',
    createdAt: '2026-08-20T07:12:00',
    lu: false,
    premierParagraphe: 'Nuit correcte, préparation à 71.',
    ...overrides,
  };
}

async function setup(id: string | null) {
  await TestBed.configureTestingModule({
    imports: [Noctua],
    providers: [
      provideHttpClient(),
      provideHttpClientTesting(),
      provideRouter([]),
      {
        provide: ActivatedRoute,
        useValue: { snapshot: { paramMap: convertToParamMap(id ? { id } : {}) } },
      },
    ],
  }).compileComponents();

  const fixture = TestBed.createComponent(Noctua);
  const httpMock = TestBed.inject(HttpTestingController);
  return { fixture, component: fixture.componentInstance, httpMock };
}

describe('Noctua', () => {
  let httpMock: HttpTestingController;

  afterEach(() => httpMock.verify());

  it('loads the briefing list when no id is in the route', async () => {
    const { fixture, component, httpMock: mock } = await setup(null);
    httpMock = mock;
    fixture.detectChanges();
    httpMock.expectOne(`${environment.apiUrl}/noctua/non-lus`).flush({ count: 0 });

    const briefing = buildBriefing();
    httpMock.expectOne(`${environment.apiUrl}/noctua/briefings?jours=14`).flush([briefing]);

    expect(component.briefings()).toEqual([briefing]);
    expect(component.loading()).toBe(false);
    expect(component.selectedId()).toBeNull();
  });

  it('loads the briefing detail and marks it read when an id is in the route', async () => {
    const { fixture, component, httpMock: mock } = await setup('3');
    httpMock = mock;
    fixture.detectChanges();
    httpMock.expectOne(`${environment.apiUrl}/noctua/non-lus`).flush({ count: 0 });

    const detail: NoctuaBriefingDetailDto = {
      briefing: buildBriefing(),
      messages: [
        { role: 'USER', content: 'Réveil — 07h12' },
        { role: 'AI', content: 'Nuit correcte, préparation à 71.' },
      ],
    };
    httpMock.expectOne(`${environment.apiUrl}/noctua/briefings/3`).flush(detail);
    httpMock.expectOne(`${environment.apiUrl}/noctua/briefings/3/lu`).flush(null);
    httpMock.expectOne(`${environment.apiUrl}/noctua/non-lus`).flush({ count: 0 });

    expect(component.selectedId()).toBe(3);
    expect(component.detail()).toEqual(detail);
  });

  it('envoyer appends the user message optimistically then the reply once it arrives', async () => {
    const { fixture, component, httpMock: mock } = await setup('3');
    httpMock = mock;
    fixture.detectChanges();
    httpMock.expectOne(`${environment.apiUrl}/noctua/non-lus`).flush({ count: 0 });

    const detail: NoctuaBriefingDetailDto = { briefing: buildBriefing(), messages: [] };
    httpMock.expectOne(`${environment.apiUrl}/noctua/briefings/3`).flush(detail);
    httpMock.expectOne(`${environment.apiUrl}/noctua/briefings/3/lu`).flush(null);
    httpMock.expectOne(`${environment.apiUrl}/noctua/non-lus`).flush({ count: 0 });

    component.userInput.set('Et demain ?');
    component.envoyer();

    expect(component.detail()?.messages).toEqual([{ role: 'USER', content: 'Et demain ?' }]);
    expect(component.sending()).toBe(true);

    httpMock
      .expectOne(`${environment.apiUrl}/noctua/briefings/3/messages`)
      .flush({ conversationId: 3, reply: 'Charge un peu élevée cette semaine.' });

    expect(component.sending()).toBe(false);
    expect(component.detail()?.messages).toEqual([
      { role: 'USER', content: 'Et demain ?' },
      { role: 'AI', content: 'Charge un peu élevée cette semaine.' },
    ]);
  });

  it('iconFor and labelFor map each briefing type', async () => {
    const { fixture, component, httpMock: mock } = await setup(null);
    httpMock = mock;
    fixture.detectChanges();
    httpMock.expectOne(`${environment.apiUrl}/noctua/non-lus`).flush({ count: 0 });
    httpMock.expectOne(`${environment.apiUrl}/noctua/briefings?jours=14`).flush([]);

    expect(component.iconFor('REVEIL')).toBe('wb_sunny');
    expect(component.iconFor('ACTIVITE')).toBe('directions_run');
    expect(component.iconFor('COUCHER')).toBe('bedtime');
    expect(component.labelFor('COUCHER')).toBe('noctua.typeCoucher');
  });
});
