import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { describe, it, expect, vi } from 'vitest';
import { App } from './app';
import { PwaUpdateService } from './service/pwa-update.service';

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
