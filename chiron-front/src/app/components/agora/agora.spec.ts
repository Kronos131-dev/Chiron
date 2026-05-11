import { ComponentFixture, TestBed } from '@angular/core/testing';

import { Agora } from './agora';

describe('Agora', () => {
  let component: Agora;
  let fixture: ComponentFixture<Agora>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Agora],
    }).compileComponents();

    fixture = TestBed.createComponent(Agora);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
