# Backend test checklist

## Placement
* [ ] The package mirrors the production package.
* [ ] The package routes it to the intended runner: `controller/`, `persistence/` and `migration/` run
      only under `mvn verify`.
* [ ] The class is named `<Subject>Test`.
* [ ] Methods are named `method_scenario_expectation`.

## Structure
* [ ] The body is structured `// Given`, `// When`, `// Then`.
* [ ] No other comments were added anywhere.
* [ ] Assertions use AssertJ `assertThat`, not bare JUnit assertions.
* [ ] Entities are built with their Lombok builders.
* [ ] The test asserts on values, not on interaction counts, unless the interaction is the behaviour.

## Spring Boot 4 imports
* [ ] `@WebMvcTest` from `org.springframework.boot.webmvc.test.autoconfigure`.
* [ ] `@DataJpaTest` from `org.springframework.boot.data.jpa.test.autoconfigure`.
* [ ] `@MockitoBean`, not the removed `@MockBean`.
* [ ] A controller slice carries `excludeAutoConfiguration = SecurityAutoConfiguration.class` and
      `@Import(JacksonAutoConfiguration.class)`.

## Coverage
* [ ] The empty-result path is covered.
* [ ] The refusal path of any ownership check is covered — no framework enforces it.
* [ ] A boundary is covered: nullable `getIsPublic()`, empty list, missing date.
* [ ] For an AI tool, the returned sentence is asserted in the happy, empty and refused cases.
* [ ] It is understood that a passing `@WebMvcTest` proves the payload, never the authorization.

## Running
* [ ] The class was run alone with `mvn -Dtest=<Class> test`.
* [ ] `mvn verify` passes.
* [ ] No `UnnecessaryStubbingException` was silenced by loosening the test.
* [ ] The test passes both alone and inside the full suite.
