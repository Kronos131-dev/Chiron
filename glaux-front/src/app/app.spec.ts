import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { describe, it, expect, vi } from 'vitest';
import { App } from './app';
import { PwaUpdateService } from './service/pwa-update.service';

function fakeTouch(clientX: number, clientY: number): Partial<TouchEvent> {
  const touch = { clientX, clientY } as Touch;
  const list = [touch] as unknown as TouchList;
  return { touches: list, changedTouches: list };
}

@Component({ selector: 'app-dummy', standalone: true, template: '' })
class Dummy {}

const SWIPE_ROUTES = [
  { path: 'noctua', component: Dummy },
  { path: '', component: Dummy },
  { path: 'coeur', component: Dummy },
  { path: 'sommeil', component: Dummy },
  { path: 'activite', component: Dummy },
  { path: 'login', component: Dummy },
];

describe('App', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideRouter([]), { provide: PwaUpdateService, useValue: { init: vi.fn() } }],
    }).compileComponents();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(App);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('renders the router outlet', () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('router-outlet')).toBeTruthy();
  });

  it('starts the pwa update watcher on creation', () => {
    const pwaUpdate = TestBed.inject(PwaUpdateService);
    TestBed.createComponent(App);
    expect(pwaUpdate.init).toHaveBeenCalled();
  });
});

describe('App swipe navigation', () => {
  async function setupOn(path: string) {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [
        provideRouter(SWIPE_ROUTES),
        { provide: PwaUpdateService, useValue: { init: vi.fn() } },
      ],
    }).compileComponents();

    const router = TestBed.inject(Router);
    await router.navigateByUrl(path);
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    return { component: fixture.componentInstance, router, fixture };
  }

  it('swiping right to left moves to the next tab in the bottom nav order', async () => {
    const { component, router, fixture } = await setupOn('/coeur');

    component.onTouchStart(fakeTouch(300, 200) as TouchEvent);
    component.onTouchEnd(fakeTouch(200, 200) as TouchEvent);
    await fixture.whenStable();

    expect(router.url).toBe('/sommeil');
  });

  it('swiping left to right moves to the previous tab in the bottom nav order', async () => {
    const { component, router, fixture } = await setupOn('/sommeil');

    component.onTouchStart(fakeTouch(100, 200) as TouchEvent);
    component.onTouchEnd(fakeTouch(200, 200) as TouchEvent);
    await fixture.whenStable();

    expect(router.url).toBe('/coeur');
  });

  it('does nothing past the last tab', async () => {
    const { component, router, fixture } = await setupOn('/activite');

    component.onTouchStart(fakeTouch(300, 200) as TouchEvent);
    component.onTouchEnd(fakeTouch(200, 200) as TouchEvent);
    await fixture.whenStable();

    expect(router.url).toBe('/activite');
  });

  it('does nothing before the first tab', async () => {
    const { component, router, fixture } = await setupOn('/noctua');

    component.onTouchStart(fakeTouch(100, 200) as TouchEvent);
    component.onTouchEnd(fakeTouch(200, 200) as TouchEvent);
    await fixture.whenStable();

    expect(router.url).toBe('/noctua');
  });

  it('ignores a mostly-vertical drag', async () => {
    const { component, router, fixture } = await setupOn('/coeur');

    component.onTouchStart(fakeTouch(300, 100) as TouchEvent);
    component.onTouchEnd(fakeTouch(290, 400) as TouchEvent);
    await fixture.whenStable();

    expect(router.url).toBe('/coeur');
  });

  it('ignores a horizontal drag below the threshold', async () => {
    const { component, router, fixture } = await setupOn('/coeur');

    component.onTouchStart(fakeTouch(300, 200) as TouchEvent);
    component.onTouchEnd(fakeTouch(280, 200) as TouchEvent);
    await fixture.whenStable();

    expect(router.url).toBe('/coeur');
  });

  it('is inactive on a route outside the bottom nav, like a Noctua briefing thread', async () => {
    const { component, router, fixture } = await setupOn('/coeur');
    await router.navigateByUrl('/login');

    component.onTouchStart(fakeTouch(300, 200) as TouchEvent);
    component.onTouchEnd(fakeTouch(200, 200) as TouchEvent);
    await fixture.whenStable();

    expect(router.url).toBe('/login');
  });
});
