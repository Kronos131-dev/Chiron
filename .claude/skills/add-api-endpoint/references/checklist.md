# Endpoint checklist

## Shape
* [ ] The path keeps the `/api` prefix and matches the domain vocabulary.
* [ ] The endpoint extends the controller that already owns the domain, or a justified vertical slice.
* [ ] Request and response DTOs are Java `record`s; no new Lombok `@Builder` DTO class.
* [ ] No entity is returned from a controller.
* [ ] Repository methods return `Optional<T>` or `List<T>`, never `null`.

## Behaviour
* [ ] Business logic is in a `@Service`, not in the controller.
* [ ] `@Transactional` is on the service write methods and nowhere else.
* [ ] Errors are `NoSuchElementException`, `IllegalArgumentException` or `SecurityException`; no bare
      `RuntimeException`.
* [ ] Lazy associations are fetched in the query, not touched after the transaction.

## Authorization
* [ ] The caller is taken from the injected `Authentication`, not from a request parameter.
* [ ] An endpoint touching another athlete's data checks owner / admin / public profile / coach.
* [ ] `getIsPublic()` is null-checked before being dereferenced.
* [ ] `SecurityConfig` was changed only if the endpoint is genuinely public.

## Frontend
* [ ] A method was added to `service/chiron-api.ts`; no component calls `HttpClient` directly.
* [ ] The TypeScript interface mirrors the DTO field for field.
* [ ] Dates are handled as ISO strings.

## Verification
* [ ] A service unit test covers the rule, including the refusal path.
* [ ] A `@WebMvcTest` covers the mapping and the payload.
* [ ] `mvn verify` passes — the controller test does not run under `mvn test`.
* [ ] An entity change ships with its Flyway migration.
