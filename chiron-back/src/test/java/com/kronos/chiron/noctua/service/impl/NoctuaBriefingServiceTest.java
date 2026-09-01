package com.kronos.chiron.noctua.service.impl;

import com.kronos.chiron.coach.agent.AiUnavailableException;
import com.kronos.chiron.coach.model.AgentType;
import com.kronos.chiron.coach.model.Conversation;
import com.kronos.chiron.coach.service.ConversationService;
import com.kronos.chiron.coach.service.MemoryNoteService;
import com.kronos.chiron.noctua.agent.NoctuaAgentRouter;
import com.kronos.chiron.noctua.model.NoctuaBriefing;
import com.kronos.chiron.noctua.model.NoctuaBriefingType;
import com.kronos.chiron.noctua.persistence.NoctuaBriefingRepository;
import com.kronos.chiron.utilisateur.model.Utilisateur;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NoctuaBriefingServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 20);

    @Mock
    private NoctuaBriefingRepository noctuaBriefingRepository;
    @Mock
    private ConversationService conversationService;
    @Mock
    private MemoryNoteService memoryNoteService;
    @Mock
    private NoctuaAgentRouter noctuaAgentRouter;
    @Mock
    private com.kronos.chiron.push.service.WebPushService webPushService;
    @Spy
    private Clock clock = Clock.fixed(Instant.parse("2026-08-20T07:00:00Z"), ZoneId.of("Europe/Paris"));

    @InjectMocks
    private NoctuaBriefingServiceImpl noctuaBriefingService;

    private Utilisateur user;
    private Conversation conversation;

    @BeforeEach
    void setUp() {
        user = Utilisateur.builder().id(7L).username("athlete").build();
        conversation = Conversation.builder().id(55L).utilisateur(user).agent(AgentType.NOCTUA).build();
        when(memoryNoteService.formatForPrompt(any(), org.mockito.ArgumentMatchers.anyInt())).thenReturn("");
    }

    @Test
    void genererSiNecessaire_dejaProduit_neCreeRienEtNAppellePasLeModele() {
        when(noctuaBriefingRepository.existsByUtilisateurAndCleDeclencheur(user, "REVEIL:" + TODAY))
                .thenReturn(true);

        Optional<NoctuaBriefing> result = noctuaBriefingService.genererSiNecessaire(user, NoctuaBriefingType.REVEIL,
                TODAY, "REVEIL:" + TODAY, "Réveil — 20 août", "Réveil — 07h12", "commande");

        assertThat(result).isEmpty();
        verify(conversationService, never()).getOrCreate(any(), any(), any());
        verify(noctuaAgentRouter, never()).chat(anyString(), anyString());
    }

    @Test
    void genererSiNecessaire_reponseDuModele_creeLaConversationEtLeBriefing() {
        when(noctuaBriefingRepository.existsByUtilisateurAndCleDeclencheur(user, "REVEIL:" + TODAY))
                .thenReturn(false);
        when(conversationService.getOrCreate(user, null, AgentType.NOCTUA)).thenReturn(conversation);
        when(noctuaAgentRouter.chat(eq("55"), anyString()))
                .thenReturn("Nuit correcte, préparation à 71.");
        when(noctuaBriefingRepository.save(any(NoctuaBriefing.class))).thenAnswer(i -> i.getArgument(0));

        Optional<NoctuaBriefing> result = noctuaBriefingService.genererSiNecessaire(user, NoctuaBriefingType.REVEIL,
                TODAY, "REVEIL:" + TODAY, "Réveil — 20 août", "Réveil — 07h12", "commande");

        assertThat(result).isPresent();
        assertThat(conversation.getTitre()).isEqualTo("Réveil — 20 août");
        verify(conversationService).recordExchange(conversation, "Réveil — 07h12", "Nuit correcte, préparation à 71.");
        verify(noctuaBriefingRepository).save(any(NoctuaBriefing.class));
        verify(webPushService).envoyerAuxAbonnements(eq(user), eq("Réveil"), eq("Nuit correcte, préparation à 71."),
                anyString());
    }

    @Test
    void genererSiNecessaire_modeleIndisponible_neCreeAucuneLigne() {
        when(noctuaBriefingRepository.existsByUtilisateurAndCleDeclencheur(user, "REVEIL:" + TODAY))
                .thenReturn(false);
        when(conversationService.getOrCreate(user, null, AgentType.NOCTUA)).thenReturn(conversation);
        when(noctuaAgentRouter.chat(eq("55"), anyString()))
                .thenThrow(new AiUnavailableException("indisponible", new RuntimeException("boom")));

        Optional<NoctuaBriefing> result = noctuaBriefingService.genererSiNecessaire(user, NoctuaBriefingType.REVEIL,
                TODAY, "REVEIL:" + TODAY, "Réveil — 20 août", "Réveil — 07h12", "commande");

        assertThat(result).isEmpty();
        verify(conversationService, never()).recordExchange(any(), any(), any());
        verify(noctuaBriefingRepository, never()).save(any());
    }

    @Test
    void genererSiNecessaire_doublonConcurrentSurLaContrainteUnique_absorbeLException() {
        when(noctuaBriefingRepository.existsByUtilisateurAndCleDeclencheur(user, "REVEIL:" + TODAY))
                .thenReturn(false);
        when(conversationService.getOrCreate(user, null, AgentType.NOCTUA)).thenReturn(conversation);
        when(noctuaAgentRouter.chat(eq("55"), anyString()))
                .thenReturn("Bilan de la nuit.");
        when(noctuaBriefingRepository.save(any(NoctuaBriefing.class)))
                .thenThrow(new DataIntegrityViolationException("contrainte unique violée"));

        Optional<NoctuaBriefing> result = noctuaBriefingService.genererSiNecessaire(user, NoctuaBriefingType.REVEIL,
                TODAY, "REVEIL:" + TODAY, "Réveil — 20 août", "Réveil — 07h12", "commande");

        assertThat(result).isEmpty();
    }

    @Test
    void dejaProduit_delegatesToRepository() {
        when(noctuaBriefingRepository.existsByUtilisateurAndCleDeclencheur(user, "ACTIVITE:9")).thenReturn(true);

        assertThat(noctuaBriefingService.dejaProduit(user, "ACTIVITE:9")).isTrue();
    }
}
