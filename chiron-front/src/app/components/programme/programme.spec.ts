import { ComponentFixture, TestBed } from '@angular/core/testing';

import { Programme } from './programme';

describe('Programme', () => {
  let component: Programme;
  let fixture: ComponentFixture<Programme>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Programme],
    }).compileComponents();

    fixture = TestBed.createComponent(Programme);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
