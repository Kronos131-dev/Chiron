import { TestBed } from '@angular/core/testing';

import { ChironApi } from './chiron-api';

describe('ChironApi', () => {
  let service: ChironApi;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(ChironApi);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
