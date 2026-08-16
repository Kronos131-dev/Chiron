package com.kronos.chiron.coach.tools;

import com.kronos.chiron.nutrition.client.OlympusClient;
import com.kronos.chiron.nutrition.service.NutritionService;
import com.kronos.chiron.utilisateur.model.Utilisateur;
import com.kronos.chiron.utilisateur.persistence.UtilisateurRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NutritionToolsTest {

    @Mock
    private UtilisateurRepository utilisateurRepository;
    @Mock
    private NutritionService nutritionService;
    @Mock
    private OlympusClient olympusClient;
    @Mock
    private ToolUserResolver toolUserResolver;

    @Spy
    private Clock clock = Clock.fixed(Instant.parse("2026-08-16T10:00:00Z"), ZoneId.of("Europe/Paris"));

    @InjectMocks
    private NutritionTools nutritionTools;

    private final JsonMapper json = new JsonMapper();

    /** Id de conversation, volontairement différent de l'id de l'utilisateur lié (7L) :
     * reproduit le bug historique où le coach interprétait le memoryId comme un id utilisateur
     * et retombait sur un utilisateur sans liaison Olympus. */
    private static final String MEMORY_ID = "42";

    private Utilisateur linkedUser;

    @BeforeEach
    void setUp() {
        linkedUser = Utilisateur.builder().id(7L).username("athlete").build();
        when(toolUserResolver.load(MEMORY_ID)).thenReturn(linkedUser);
    }

    @Test
    void getPlanningRepas_userLinked_returnsPlanningNotNotLinkedMessage() {
        when(nutritionService.getValidToken("athlete")).thenReturn("olympus-token");
        JsonNode plan = json.readTree("{\"entries\": []}");
        JsonNode presets = json.readTree("[]");
        when(olympusClient.getWeeklyPlan("olympus-token")).thenReturn(plan);
        when(olympusClient.getMealPresets("olympus-token")).thenReturn(presets);

        String result = nutritionTools.getPlanningRepas(MEMORY_ID);

        assertThat(result).contains("Planning hebdomadaire");
        assertThat(result).doesNotContain("n'a pas lié son compte Olympus");
    }

    @Test
    void getPlanningRepas_userNotLinked_returnsNotLinkedMessage() {
        when(nutritionService.getValidToken("athlete")).thenThrow(new NutritionService.NotLinkedException());

        String result = nutritionTools.getPlanningRepas(MEMORY_ID);

        assertThat(result).contains("n'a pas lié son compte Olympus");
    }

    @Test
    void getPlanningRepas_olympusRejectsToken_returnsExpiredMessage() {
        when(nutritionService.getValidToken("athlete")).thenReturn("olympus-token");
        when(olympusClient.getWeeklyPlan("olympus-token"))
                .thenThrow(new OlympusClient.OlympusUnauthorizedException("401"));

        String result = nutritionTools.getPlanningRepas(MEMORY_ID);

        assertThat(result).contains("liaison Olympus").contains("expiré");
    }
}
