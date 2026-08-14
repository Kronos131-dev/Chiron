# Layer skeletons

Copy the structure of each block. The shapes are taken from the existing `controller/`, `service/`,
`repository/` and `dto/` packages.

## DTO — a Java record

```java
package com.kronos.chiron.dto;

import java.time.LocalDateTime;
import java.util.List;

public record SeanceDto(
        Long id,
        String titre,
        String note,
        LocalDateTime startTime,
        LocalDateTime endTime,
        Integer weekNumber,
        boolean isModele,
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
package com.kronos.chiron.repository;

public interface SeanceRepository extends JpaRepository<Seance, Long> {

    List<Seance> findByUtilisateurUsernameAndIsModeleFalseOrderByDisplayOrderAscStartTimeDesc(
            String username);

    Optional<Seance> findByIdAndUtilisateurUsername(Long id, String username);
}
```

Derived names are long here by convention. `Optional<T>` for one, `List<T>` for many, never `null`.

## Service

```java
package com.kronos.chiron.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EtatJournalierService {

    private final EtatJournalierRepository etatJournalierRepository;
    private final UtilisateurRepository utilisateurRepository;

    public EtatJournalierDto getForDate(String caller, LocalDate date) {
        Utilisateur user = utilisateurRepository.findByUsername(caller)
                .orElseThrow(() -> new NoSuchElementException("Utilisateur introuvable"));

        return etatJournalierRepository.findByUtilisateurAndDate(user, date)
                .map(EtatJournalierDto::from)
                .orElseThrow(() -> new NoSuchElementException("Aucun état pour le " + date));
    }

    @Transactional
    public EtatJournalierDto save(String caller, EtatJournalierDto dto) {
        if (dto.sommeil() < 1 || dto.sommeil() > 5) {
            throw new IllegalArgumentException("Le sommeil doit être noté de 1 à 5");
        }
        // …
    }
}
```

`@Transactional` on writes only, and only here.

## Controller

```java
package com.kronos.chiron.controller;

@RestController
@RequestMapping("/api/etat-journalier")
public class EtatJournalierController {

    private final EtatJournalierService etatJournalierService;

    public EtatJournalierController(EtatJournalierService etatJournalierService) {
        this.etatJournalierService = etatJournalierService;
    }

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
| `NoSuchElementException` | 404 |
| `IllegalArgumentException` | 400 |
| `SecurityException` | 403 |
| `AiUnavailableException` | 503 |

Body is always `{"error": "<message>"}`. Anything else becomes an opaque 500.
