# Where an endpoint goes

## One shape, every module

`com.kronos.chiron` is organised by business domain. There is no horizontal `controller/`,
`service/`, `repository/`, `entity/` or `dto/` root package — every module carries its own:

```
<module>/controller/     @RestController, @RequestMapping("/api/…"), returns ResponseEntity<Dto>
<module>/dto/            Java records
<module>/model/          JPA entities and their domain enums
<module>/persistence/    Spring Data JPA interfaces, Repository suffix
<module>/service/        the interface, plus the nested exception types callers catch by name
<module>/service/impl/   @Service, @RequiredArgsConstructor, @Transactional on writes, Impl suffix
<module>/mapper/         MapStruct, @Mapper(config = CentralMapperConfig.class)
```

Two extra package names exist where no layer name fits: `client/` for code that speaks to an
external API (`fitbit/client/FitbitClient`, `nutrition/client/OlympusClient`), and `configuration/`
for a module-local Spring configuration (`nutrition/configuration/OlympusDbConfig`).

The package name decides the test phase — `controller/` and `persistence/` run under Failsafe,
everything else under Surefire. Filing a class in the wrong one silently moves its test.

## The modules

| Module | Surface |
|--------|---------|
| `seance/` | the core domain: `Seance`, `Exercice`, `Serie`, `Degressif`, journal, `SeanceMapper` |
| `programme/` | building, reordering and copying programmes |
| `exercice/` | the standardised exercise library and its importer |
| `utilisateur/` | profile, settings, the `Utilisateur` entity and its enums |
| `auth/` | registration, login, password reset |
| `coach/` | the AI subsystem: `agent/`, `tools/`, `configuration/`, conversations, memory notes |
| `journalier/` | daily state and recovery |
| `performance/` | 1RM records and tiers |
| `agora/` | the social listing |
| `stats/` | server-side aggregation for the statistics screen |
| `fitbit/` | Google Health OAuth2/PKCE, sync service, parser, session store |
| `nutrition/` | Olympus HTTP client, token service, read-only JDBC pool |
| `visbody/` | body-composition PDFs parsed from a Gmail mailbox |
| `boditrax/` | CSV import, sharing Visbody's persistence path |
| `core/` | `exceptions/`, `security/`, `configuration/` — shared, owned by no domain |
| `security/` | `SecurityConfig`, `JwtService`, `JwtAuthenticationFilter`, `WebMvcConfig` |

## Which to choose

Extend an existing module when the endpoint belongs to a domain the app already owns — a new
journal filter, a new profile field, another programme operation.

Open a new module when the feature is self-contained, with its own vocabulary and its own failure
modes. The test is whether its DTOs would be meaningless to the rest of the app.

## Controller shape

`@RequiredArgsConstructor` over `private final` fields, never field `@Autowired`. Methods return
`ResponseEntity<Dto>` or `ResponseEntity<List<Dto>>`, never an entity. No `try`/`catch`: exceptions
travel to `core/exceptions/GlobalExceptionHandler`. The caller comes from
`core/security/AuthenticatedUserService`, never from a `username` request parameter.
