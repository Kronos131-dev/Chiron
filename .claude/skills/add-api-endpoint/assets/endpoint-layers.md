# Layer skeletons

Copy the structure of each block. The shapes are taken from the existing `controller/`, `service/`,
`persistence/` and `dto/` packages.

## DTO — a Java record

```java
package com.kronos.chiron.seance.dto;

import java.time.LocalDateTime;
import java.util.List;

public record SeanceDto(
        Long id,
        String titre,
        String note,
        LocalDateTime startTime,
        LocalDateTime endTime,
        Integer weekNumber,
        Boolean historique,
        ProfileDto utilisateur,
        List<ExerciceDto> exercices
) {}
```

With a static factory when the mapping is trivial and used in one place:

```java
public record EtatJournalierDto(LocalDate date, int sommeil, int fatigue, int energie) {

    public static EtatJournalierDto from(EtatJournalier etat) {
        return new EtatJournalierDto(etat.getDate(), etat.getSommeil(), etat.getFatigue(),
                etat.getEnergie());
    }
}
```

## Repository

```java
package com.kronos.chiron.seance.persistence;

public interface SeanceRepository extends JpaRepository<Seance, Long> {

    List<Seance> findByUtilisateurUsernameAndHistoriqueFalseOrderByDisplayOrderAscStartTimeDesc(
            String username);

    Optional<Seance> findByIdAndUtilisateurUsername(Long id, String username);
}
```

Derived names are long here by convention. `Optional<T>` for one, `List<T>` for many, never `null`.

## Service — interface, then implementation

```java
package com.kronos.chiron.journalier.service;

public interface EtatJournalierService {

    EtatJournalierDto getForDate(String caller, LocalDate date);

    EtatJournalierDto save(String caller, EtatJournalierDto dto);
}
```

```java
package com.kronos.chiron.journalier.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.kronos.chiron.core.exceptions.ErrorFactory.badRequest;
import static com.kronos.chiron.core.exceptions.ErrorFactory.notFound;

@Service
@RequiredArgsConstructor
public class EtatJournalierServiceImpl implements EtatJournalierService {

    private final EtatJournalierRepository etatJournalierRepository;
    private final AuthenticatedUserService authenticatedUserService;

    @Override
    public EtatJournalierDto getForDate(String caller, LocalDate date) {
        Utilisateur user = authenticatedUserService.getAuthenticatedUser();

        return etatJournalierRepository.findByUtilisateurAndDate(user, date)
                .map(EtatJournalierDto::from)
                .orElseThrow(() -> notFound("Aucun état pour le " + date));
    }

    @Transactional
    @Override
    public EtatJournalierDto save(String caller, EtatJournalierDto dto) {
        if (dto.sommeil() < 1 || dto.sommeil() > 5) {
            throw badRequest("Le sommeil doit être noté de 1 à 5");
        }
        // …
    }
}
```

`@Transactional` on writes only, and only here. Never a bare exception — `ErrorFactory` carries the
status. Resolve the caller through `AuthenticatedUserService`, never from a request parameter.

## Controller

```java
package com.kronos.chiron.journalier.controller;

@RestController
@RequestMapping("/api/etat-journalier")
@RequiredArgsConstructor
public class EtatJournalierController {

    private final EtatJournalierService etatJournalierService;

    @GetMapping
    public ResponseEntity<EtatJournalierDto> get(Authentication authentication,
                                                 @RequestParam LocalDate date) {
        return ResponseEntity.ok(etatJournalierService.getForDate(authentication.getName(), date));
    }

    @PostMapping
    public ResponseEntity<EtatJournalierDto> save(Authentication authentication,
                                                  @RequestBody EtatJournalierDto dto) {
        return ResponseEntity.ok(etatJournalierService.save(authentication.getName(), dto));
    }
}
```

The caller comes from `Authentication`. A `username` request parameter names the *target*, never the
caller.

## Frontend facade method

`chiron-front/src/app/service/chiron-api.ts`:

```ts
export interface EtatJournalier {
  date: string;
  sommeil: number;
  fatigue: number;
  energie: number;
}

  getEtatJournalier(date: string): Observable<EtatJournalier> {
    return this.http.get<EtatJournalier>(`${this.apiUrl}/etat-journalier`, { params: { date } });
  }

  saveEtatJournalier(etat: EtatJournalier): Observable<EtatJournalier> {
    return this.http.post<EtatJournalier>(`${this.apiUrl}/etat-journalier`, etat);
  }
```

The interface mirrors the record field for field. `LocalDate` and `LocalDateTime` arrive as ISO
strings.

## Error mapping, for reference

| Thrown in the service | Status returned |
|-----------------------|-----------------|
| `ErrorFactory.notFound(…)` | 404 |
| `ErrorFactory.badRequest(…)` | 400 |
| `ErrorFactory.forbidden(…)` | 403 |
| `ErrorFactory.conflict(…)` | 409 |
| `ErrorFactory.unauthorized(…)` | 401 |
| `ChironTechnicalException` | 500 |
| `MethodArgumentNotValidException` | 400, fields joined |
| `AiUnavailableException` | 503 |

Body is always `{"error": "<message>"}`. Anything else becomes an opaque 500.
