import { ComponentFixture, TestBed } from '@angular/core/testing';

import { Session } from './session';

describe('Session', () => {
  let component: Session;
  let fixture: ComponentFixture<Session>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Session],
    }).compileComponents();

    fixture = TestBed.createComponent(Session);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
