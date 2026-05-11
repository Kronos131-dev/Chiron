import { ComponentFixture, TestBed } from '@angular/core/testing';

import { Journal } from './journal';

describe('Journal', () => {
  let component: Journal;
  let fixture: ComponentFixture<Journal>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Journal],
    }).compileComponents();

    fixture = TestBed.createComponent(Journal);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
