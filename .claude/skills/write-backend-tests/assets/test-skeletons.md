# Test skeletons

Copy the structure. Every import block is taken verbatim from an existing test — these are the Spring
Boot 4 packages, which differ from every Boot 3 example.

## Unit test — service, AI tool, mapper, helper

Runs under `mvn test`.

```java
package com.kronos.chiron.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProgrammeServiceTest {

    @Mock private SeanceRepository seanceRepository;
    @Mock private UtilisateurRepository utilisateurRepository;
    @InjectMocks private ProgrammeService programmeService;

    @Test
    void getProgrammes_privateProfileAndNotACoach_throwsSecurityException() {
        // Given
        Utilisateur caller = Utilisateur.builder().id(1L).username("kronos").role(Role.USER).build();
        Utilisateur target = Utilisateur.builder().id(2L).username("athena").isPublic(false).build();
        when(utilisateurRepository.findByUsername("kronos")).thenReturn(Optional.of(caller));
        when(utilisateurRepository.findByUsername("athena")).thenReturn(Optional.of(target));

        // When / Then
        assertThatThrownBy(() -> programmeService.getProgrammes("kronos", "athena"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("privé");
    }
}
```

## Controller slice

Lives in `controller/`, runs under `mvn verify`. Header copied from `JournalControllerTest`:

```java
package com.kronos.chiron.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Import(JacksonAutoConfiguration.class)
@WebMvcTest(value = JournalController.class,
            excludeAutoConfiguration = {SecurityAutoConfiguration.class})
class JournalControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private SeanceRepository seanceRepository;
    @MockitoBean private SeanceMapper seanceMapper;
    @MockitoBean private JwtService jwtService;
    @MockitoBean private UserDetailsService userDetailsService;

    @Test
    void getHistorique_noSessions_returnsEmptyArray() throws Exception {
        // Given
        when(seanceRepository.findByUtilisateurUsername("kronos")).thenReturn(List.of());

        // When / Then
        mockMvc.perform(get("/api/journal/historique").param("username", "kronos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
```

`JwtService` and `UserDetailsService` are mocked even when unused: the slice still wires the
application's security beans. `excludeAutoConfiguration = SecurityAutoConfiguration.class` means this
test proves the **mapping and the payload**, never the authorization — that needs a service-level
test.

## Repository slice

Lives in `persistence/`, runs under `mvn verify`, on H2 in PostgreSQL mode. Header copied from
`SeanceRepositoryTest`:

```java
package com.kronos.chiron.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class SeanceRepositoryTest {

    @Autowired private TestEntityManager em;
    @Autowired private SeanceRepository seanceRepository;

    private Utilisateur user;

    @BeforeEach
    void setUp() {
        user = em.persist(Utilisateur.builder().username("kronos").build());
    }

    @Test
    void findByUtilisateurUsername_returnsOnlyThatUsersSessions() {
        // Given
        em.persist(Seance.builder().utilisateur(user).titre("Push").build());

        // When
        List<Seance> found = seanceRepository.findByUtilisateurUsername("kronos");

        // Then
        assertThat(found).hasSize(1);
        assertThat(found.getFirst().getTitre()).isEqualTo("Push");
    }
}
```

Persist the parent before the child, or Hibernate reports `detached entity passed to persist`.

## AI tool test

An `coach/tools/*Tools` method is ordinary Java. Assert on the **sentence**, since the model relays it.

```java
@ExtendWith(MockitoExtension.class)
class RecoveryToolsTest {

    @Mock private UtilisateurRepository utilisateurRepository;
    @Mock private EtatJournalierRepository etatJournalierRepository;
    @InjectMocks private RecoveryTools recoveryTools;

    @Test
    void getEtatDuJour_noEntryToday_returnsExplanatorySentence() {
        // Given
        Utilisateur user = Utilisateur.builder().id(1L).username("kronos").build();
        when(utilisateurRepository.findById(1L)).thenReturn(Optional.of(user));
        when(etatJournalierRepository.findByUtilisateurAndDate(user, LocalDate.now()))
                .thenReturn(Optional.empty());

        // When
        String answer = recoveryTools.getEtatDuJour("1");

        // Then
        assertThat(answer).isEqualTo("Aucun état journalier renseigné aujourd'hui.");
    }
}
```

The `@ToolMemoryId` argument is the user's numeric id **as a string**.

## Migration

Nothing new to write. `migration/FlywaySchemaValidationTest` replays every migration from V0 on a
real `postgres:16-alpine` through Testcontainers with `ddl-auto: validate`, and asserts the context
starts. Adding a migration is covered by running `mvn verify`.
