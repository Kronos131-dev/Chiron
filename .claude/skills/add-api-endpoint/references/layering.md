# Where an endpoint goes

## The layered core

`com.kronos.chiron` keeps the general-purpose surface in horizontal layers:

```
controller/  @RestController, @RequestMapping("/api/…"), returns ResponseEntity<Dto>
service/     @Service, @RequiredArgsConstructor, @Transactional on writes
repository/  Spring Data JPA interfaces
dto/         Java records, plus dto/auth, dto/chat, dto/settings
entity/      JPA entities and domain enums
mapper/      SeanceMapper only — hand-written, no MapStruct
```

The 17 controllers: `Agora`, `Authentication`, `Boditrax`, `Chat`, `Conversation`, `EtatJournalier`,
`ExerciceDefinition`, `Fitbit`, `Journal`, `Nutrition`, `Performance`, `Profile`, `ProfileSetup`,
`Programme`, `Settings`, `Visbody`, plus `GlobalExceptionHandler`.

## The vertical slices

Integrations keep their controller, service, client and DTOs in one package. Prefer this shape for
anything that talks to a third party.

| Package | Surface |
|---------|---------|
| `stats/` | `StatsController`, `StatsService` and its 12 DTOs together |
| `fitbit/` | OAuth2/PKCE client, sync service, parser, session store, DTOs |
| `nutrition/`, `nutrition/olympusdb/` | Olympus HTTP client, token service, and a second read-only JDBC pool onto the Olympus database |
| `visbody/` | PDF parser, mail service, import service, record and repository |
| `boditrax/` | CSV parser and import service |

## Which to choose

Extend an existing controller when the endpoint belongs to a domain the app already owns — a new
journal filter, a new profile field, another programme operation.

Open a vertical slice when the feature is a self-contained integration with its own vocabulary and
its own failure modes. The test is whether its DTOs would be meaningless to the rest of the app.

## Controller shape

Existing controllers use explicit constructor injection rather than `@RequiredArgsConstructor` —
match the file being edited. Methods return `ResponseEntity<Dto>` or `ResponseEntity<List<Dto>>`.

```java
@RestController
@RequestMapping("/api/journal")
public class JournalController {

    private final SeanceRepository seanceRepository;
    private final SeanceMapper seanceMapper;

    public JournalController(SeanceRepository seanceRepository, SeanceMapper seanceMapper) {
        this.seanceRepository = seanceRepository;
        this.seanceMapper = seanceMapper;
    }
```

Some controllers call the repository directly for a plain read. That is accepted for a read with no
business rule; anything with a rule, a write, or more than one repository goes through a service.

## Mapping

There is no MapStruct. Three options, in order of preference:

1. A static factory on the record: `public static SeanceDto from(Seance seance) { … }`.
2. A method on `SeanceMapper`, if the type is already mapped there.
3. Inline construction in the service, for a one-off projection.

Do not introduce a mapping framework for one endpoint.

## Naming

Paths are lowercase and French where the domain is French: `/api/journal/historique`,
`/api/programmes`, `/api/etat-journalier`. Java identifiers follow the same split as the rest of the
codebase — French domain nouns, English technical scaffolding.
