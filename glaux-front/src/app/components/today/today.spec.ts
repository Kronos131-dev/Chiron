import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { Today } from './today';
import { environment } from '../../../environments/environment';

function fakeTouch(clientY: number): Partial<TouchEvent> {
  return { touches: [{ clientY } as Touch] as unknown as TouchList };
}

function flushTodayRequests(httpMock: HttpTestingController) {
  httpMock.expectOne(`${environment.apiUrl}/sante/resume`).flush({ linked: false });
  httpMock.expectOne(`${environment.apiUrl}/sante/sync`).flush([]);
  httpMock.expectOne(`${environment.apiUrl}/noctua/non-lus`).flush({ count: 0 });
}

describe('Today', () => {
  let component: Today;
  let fixture: ComponentFixture<Today>;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Today],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(Today);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('pulling down past the threshold at the top of the page triggers a sync', () => {
    fixture.detectChanges();
    flushTodayRequests(httpMock);
    vi.spyOn(window, 'scrollY', 'get').mockReturnValue(0);

    component.onTouchStart(fakeTouch(0) as TouchEvent);
    component.onTouchMove(fakeTouch(90) as TouchEvent);
    expect(component.pullDistance()).toBe(90);
    component.onTouchEnd();

    expect(component.syncing()).toBe(true);
    httpMock.expectOne(`${environment.apiUrl}/sante/sync`).flush([]);
    httpMock.expectOne(`${environment.apiUrl}/sante/resume`).flush({ linked: false });

    expect(component.syncing()).toBe(false);
    expect(component.pullDistance()).toBe(0);
  });

  it('releasing before the threshold resets without syncing', () => {
    fixture.detectChanges();
    flushTodayRequests(httpMock);
    vi.spyOn(window, 'scrollY', 'get').mockReturnValue(0);

    component.onTouchStart(fakeTouch(0) as TouchEvent);
    component.onTouchMove(fakeTouch(30) as TouchEvent);
    component.onTouchEnd();

    expect(component.syncing()).toBe(false);
    expect(component.pullDistance()).toBe(0);
  });

  it('does nothing when the page is not scrolled to the top', () => {
    fixture.detectChanges();
    flushTodayRequests(httpMock);
    vi.spyOn(window, 'scrollY', 'get').mockReturnValue(120);

    component.onTouchStart(fakeTouch(0) as TouchEvent);
    component.onTouchMove(fakeTouch(90) as TouchEvent);

    expect(component.pullDistance()).toBe(0);
  });

  it('caps the visual pull distance so an aggressive drag does not overshoot', () => {
    fixture.detectChanges();
    flushTodayRequests(httpMock);
    vi.spyOn(window, 'scrollY', 'get').mockReturnValue(0);

    component.onTouchStart(fakeTouch(0) as TouchEvent);
    component.onTouchMove(fakeTouch(500) as TouchEvent);

    expect(component.pullDistance()).toBe(100);
  });
});
