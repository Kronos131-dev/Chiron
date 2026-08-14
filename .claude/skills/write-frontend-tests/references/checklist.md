# Frontend spec checklist

## Worth writing
* [ ] The spec asserts a behaviour a user depends on, not that the component instantiates.
* [ ] Test names describe the behaviour, not "should work".
* [ ] The spec sits next to the code as `<name>.spec.ts`.

## Wiring
* [ ] Vitest functions are imported explicitly, as the existing specs do.
* [ ] The standalone component is in `imports`, never `declarations`.
* [ ] `ChironApi` is stubbed through `providers` with `useValue`.
* [ ] Stubs return `of(...)`, not bare objects.
* [ ] `TranslatePipe` is imported when the template uses `| t`.
* [ ] `fixture.detectChanges()` is called after any signal change, before a DOM assertion.

## Fixtures
* [ ] DTO fixtures carry every required field of the interface in `service/chiron-api.ts`.
* [ ] Repeated fixtures go through a factory function rather than duplicated literals.
* [ ] A DTO that gained a required field had every factory updated in the same change.

## `ChironApi` specs
* [ ] `provideHttpClient()` and `provideHttpClientTesting()` are both provided.
* [ ] The method, URL and body are asserted on the expected request.
* [ ] The response is flushed and the caller's result asserted.
* [ ] `httpMock.verify()` runs in `afterEach`.

## Running
* [ ] `npm test` was run.
* [ ] A `cardioType` compile failure was identified as the pre-existing baseline, not as this
      change's doing.
* [ ] If the baseline failure prevented the new spec from running, that was stated plainly rather
      than reported as a pass.
