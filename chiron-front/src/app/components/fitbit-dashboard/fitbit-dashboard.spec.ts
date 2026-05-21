import { ComponentFixture, TestBed } from '@angular/core/testing';

import { FitbitDashboard } from './fitbit-dashboard';

describe('FitbitDashboard', () => {
  let component: FitbitDashboard;
  let fixture: ComponentFixture<FitbitDashboard>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [FitbitDashboard],
    }).compileComponents();

    fixture = TestBed.createComponent(FitbitDashboard);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
