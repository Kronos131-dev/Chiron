# Tool skeletons

Copy the structure. Every block is taken from `chiron-back/src/main/java/com/kronos/chiron/coach/`.

## Component header

```java
package com.kronos.chiron.ai;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolMemoryId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RecoveryTools {

    private final UtilisateurRepository utilisateurRepository;
    private final EtatJournalierRepository etatJournalierRepository;
```

## Reading the caller's own data

```java
    @Tool("Renvoie l'état de forme du jour (sommeil, fatigue, courbatures, stress, énergie). Appelle cet outil avant de conseiller une charge de travail.")
    public String getEtatDuJour(@ToolMemoryId String userId) {
        Utilisateur user = loadUser(userId);

        return etatJournalierRepository.findByUtilisateurAndDate(user, LocalDate.now())
                .map(etat -> "Sommeil " + etat.getSommeil() + "/5, fatigue " + etat.getFatigue()
                        + "/5, courbatures " + etat.getCourbatures() + "/5, stress " + etat.getStress()
                        + "/5, énergie " + etat.getEnergie() + "/5.")
                .orElse("Aucun état journalier renseigné aujourd'hui.");
    }
```

Note the shape of the answer: a short factual French sentence with the numbers inline, and a plain
sentence rather than an exception when there is nothing to report.

## Reading another athlete's data — the ownership check

Copied from `WorkoutTools.getUserProgrammes`. Reproduce this exactly whenever a tool accepts a
`targetUsername`.

```java
    @Tool("Liste les programmes d'un utilisateur. Laisse targetUsername vide pour soi-même.")
    public String getUserProgrammes(@ToolMemoryId String userId, String targetUsername) {
        Utilisateur requestUser = utilisateurRepository.findById(Long.parseLong(userId))
                .orElseThrow(() -> new RuntimeException("Utilisateur requérant introuvable"));

        String searchUsername = (targetUsername != null && !targetUsername.isBlank())
                ? targetUsername
                : requestUser.getUsername();

        Utilisateur targetUser = utilisateurRepository.findByUsername(searchUsername).orElse(null);
        if (targetUser == null) {
            return "L'utilisateur '" + searchUsername + "' est introuvable.";
        }

        if (!requestUser.getUsername().equals(searchUsername) && requestUser.getRole() != Role.ADMIN) {
            if (targetUser.getIsPublic() == null || !targetUser.getIsPublic()) {
                return "Le profil de l'utilisateur '" + searchUsername
                        + "' est privé. Vous n'avez pas l'autorisation de voir ses programmes.";
            }
        }

        // ... the read itself
    }
```

## Writing

A write that spans more than one repository call belongs in a service, which owns the transaction.
The tool stays a thin caller and formats the confirmation.

```java
    @Tool("Enregistre une note durable sur l'utilisateur. Types : BLESSURE, PREFERENCE, OBJECTIF, ENGAGEMENT, NOTE_LIBRE.")
    public String enregistrerNote(@ToolMemoryId String userId, String type, String contenu) {
        Utilisateur user = loadUser(userId);
        if (contenu == null || contenu.isBlank()) {
            return "Le contenu de la note est vide — rien enregistré.";
        }

        memoryNoteService.enregistrer(user, MemoryNoteType.valueOf(type), contenu);
        return "Note enregistrée.";
    }
```

## The private loader

```java
    private Utilisateur loadUser(String userId) {
        return utilisateurRepository.findById(Long.parseLong(userId))
                .orElseThrow(() -> new RuntimeException("Utilisateur introuvable"));
    }
```

## The prompt line in `ChironAgent`

Add the trigger and the tool name to the existing block, in the established form:

```java
            "RÉCUPÉRATION : état de forme du jour → [getEtatDuJour] ; tendance sur la semaine → [getTendanceRecuperation].",
```

One clause per capability, `→ [nomExact]`, semicolons between clauses. The method name in brackets
must match the Java method name character for character.

## The unit test

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
