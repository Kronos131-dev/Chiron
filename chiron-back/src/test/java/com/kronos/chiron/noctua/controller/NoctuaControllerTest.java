package com.kronos.chiron.noctua.controller;

import tools.jackson.databind.json.JsonMapper;
import com.kronos.chiron.coach.agent.ConversationMemoryManager;
import com.kronos.chiron.coach.model.AgentType;
import com.kronos.chiron.coach.model.Conversation;
import com.kronos.chiron.coach.model.ConversationMessage;
import com.kronos.chiron.coach.model.MessageRole;
import com.kronos.chiron.coach.service.AiUsageService;
import com.kronos.chiron.coach.service.ConversationService;
import com.kronos.chiron.coach.service.MemoryNoteService;
import com.kronos.chiron.core.security.AuthenticatedUserService;
import com.kronos.chiron.noctua.agent.NoctuaAgentRouter;
import com.kronos.chiron.noctua.model.NoctuaBriefing;
import com.kronos.chiron.noctua.model.NoctuaBriefingType;
import com.kronos.chiron.noctua.persistence.NoctuaBriefingRepository;
import com.kronos.chiron.security.JwtService;
import com.kronos.chiron.utilisateur.model.AiProvider;
import com.kronos.chiron.utilisateur.model.Utilisateur;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(JacksonAutoConfiguration.class)
@WebMvcTest(value = NoctuaController.class, excludeAutoConfiguration = SecurityAutoConfiguration.class)
class NoctuaControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JsonMapper objectMapper;

    @MockitoBean
    private AuthenticatedUserService authenticatedUserService;
    @MockitoBean
    private NoctuaBriefingRepository noctuaBriefingRepository;
    @MockitoBean
    private ConversationService conversationService;
    @MockitoBean
    private MemoryNoteService memoryNoteService;
    @MockitoBean
    private NoctuaAgentRouter noctuaAgentRouter;
    @MockitoBean
    private ConversationMemoryManager memoryManager;
    @MockitoBean
    private AiUsageService aiUsageService;
    @MockitoBean
    private Clock clock;
    @MockitoBean
    private JwtService jwtService;
    @MockitoBean
    private UserDetailsService userDetailsService;

    private final Utilisateur user = Utilisateur.builder().id(1L).username("athlete").build();

    @Test
    void briefings_returnsSummariesOrderedByCreatedAtDesc() throws Exception {
        when(authenticatedUserService.getAuthenticatedUser()).thenReturn(user);
        when(clock.instant()).thenReturn(Instant.parse("2026-08-20T10:00:00Z"));
        when(clock.getZone()).thenReturn(ZoneId.of("Europe/Paris"));

        Conversation conversation = Conversation.builder().id(55L).utilisateur(user).agent(AgentType.NOCTUA).build();
        NoctuaBriefing briefing = NoctuaBriefing.builder().id(3L).utilisateur(user).conversation(conversation)
                .type(NoctuaBriefingType.REVEIL).dateReference(LocalDate.of(2026, 8, 20)).lu(false)
                .createdAt(LocalDateTime.of(2026, 8, 20, 7, 12)).build();
        when(noctuaBriefingRepository.findByUtilisateurAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(eq(user),
                any())).thenReturn(List.of(briefing));
        when(conversationService.getMessages(conversation)).thenReturn(List.of(
                ConversationMessage.builder().role(MessageRole.USER).content("Réveil — 07h12").build(),
                ConversationMessage.builder().role(MessageRole.AI).content("Nuit correcte, préparation à 71.")
                        .build()));

        mockMvc.perform(get("/api/noctua/briefings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(3))
                .andExpect(jsonPath("$[0].type").value("REVEIL"))
                .andExpect(jsonPath("$[0].premierParagraphe").value("Nuit correcte, préparation à 71."));
    }

    @Test
    void briefing_inconnu_returns404() throws Exception {
        when(authenticatedUserService.getAuthenticatedUser()).thenReturn(user);
        when(noctuaBriefingRepository.findByIdAndUtilisateur(99L, user)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/noctua/briefings/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void briefingParActivite_briefingExistant_returnsSummary() throws Exception {
        when(authenticatedUserService.getAuthenticatedUser()).thenReturn(user);
        Conversation conversation = Conversation.builder().id(55L).utilisateur(user).agent(AgentType.NOCTUA).build();
        NoctuaBriefing briefing = NoctuaBriefing.builder().id(3L).utilisateur(user).conversation(conversation)
                .type(NoctuaBriefingType.ACTIVITE).dateReference(LocalDate.of(2026, 8, 20)).lu(false)
                .createdAt(LocalDateTime.of(2026, 8, 20, 12, 45)).build();
        when(noctuaBriefingRepository.findByUtilisateurAndCleDeclencheur(user, "ACTIVITE:42"))
                .thenReturn(Optional.of(briefing));
        when(conversationService.getMessages(conversation)).thenReturn(List.of(
                ConversationMessage.builder().role(MessageRole.AI).content("Séance dense, effort au-dessus de la"
                        + " moyenne.").build()));

        mockMvc.perform(get("/api/noctua/briefings/par-activite/42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(3))
                .andExpect(jsonPath("$.premierParagraphe")
                        .value("Séance dense, effort au-dessus de la moyenne."));
    }

    @Test
    void briefingParActivite_aucunBriefing_returns404() throws Exception {
        when(authenticatedUserService.getAuthenticatedUser()).thenReturn(user);
        when(noctuaBriefingRepository.findByUtilisateurAndCleDeclencheur(user, "ACTIVITE:99"))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/api/noctua/briefings/par-activite/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void envoyerMessage_conversationNoctuaExistante_returnsReply() throws Exception {
        // Le briefing #3 et sa conversation #55 sont volontairement des identifiants différents :
        // reproduit le bug où le contrôleur passait l'id de briefing (issu de l'URL) directement à
        // conversationService.getOrCreate, qui attend un id de conversation.
        when(authenticatedUserService.getAuthenticatedUser()).thenReturn(user);
        Conversation conversation = Conversation.builder().id(55L).utilisateur(user).agent(AgentType.NOCTUA).build();
        NoctuaBriefing briefing = NoctuaBriefing.builder().id(3L).utilisateur(user).conversation(conversation)
                .type(NoctuaBriefingType.REVEIL).build();
        when(noctuaBriefingRepository.findByIdAndUtilisateur(3L, user)).thenReturn(Optional.of(briefing));
        when(conversationService.getOrCreate(user, 55L, AgentType.NOCTUA)).thenReturn(conversation);
        when(memoryNoteService.formatForPrompt(any(), org.mockito.ArgumentMatchers.anyInt())).thenReturn("");
        when(aiUsageService.resolveProvider(user)).thenReturn(AiProvider.MISTRAL);
        when(noctuaAgentRouter.chatWithFallback(eq(AiProvider.MISTRAL), eq("55"), anyString()))
                .thenReturn("Charge un peu élevée cette semaine.");

        mockMvc.perform(post("/api/noctua/briefings/3/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("message", "Et demain ?"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversationId").value(55))
                .andExpect(jsonPath("$.reply").value("Charge un peu élevée cette semaine."));

        verify(conversationService).recordExchange(conversation, "Et demain ?", "Charge un peu élevée cette semaine.");
    }

    @Test
    void envoyerMessage_briefingInconnu_returns404() throws Exception {
        when(authenticatedUserService.getAuthenticatedUser()).thenReturn(user);
        when(noctuaBriefingRepository.findByIdAndUtilisateur(99L, user)).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/noctua/briefings/99/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("message", "Et demain ?"))))
                .andExpect(status().isNotFound());

        verify(conversationService, org.mockito.Mockito.never()).getOrCreate(any(), any(), any());
    }

    @Test
    void marquerLu_briefingExistant_marksItRead() throws Exception {
        when(authenticatedUserService.getAuthenticatedUser()).thenReturn(user);
        NoctuaBriefing briefing = NoctuaBriefing.builder().id(3L).utilisateur(user).lu(false).build();
        when(noctuaBriefingRepository.findByIdAndUtilisateur(3L, user)).thenReturn(Optional.of(briefing));

        mockMvc.perform(post("/api/noctua/briefings/3/lu"))
                .andExpect(status().isNoContent());

        verify(noctuaBriefingRepository).save(briefing);
    }

    @Test
    void nonLus_returnsUnreadCount() throws Exception {
        when(authenticatedUserService.getAuthenticatedUser()).thenReturn(user);
        when(noctuaBriefingRepository.countByUtilisateurAndLuFalse(user)).thenReturn(2L);

        mockMvc.perform(get("/api/noctua/non-lus"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(2));
    }
}
