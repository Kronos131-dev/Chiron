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
import com.kronos.chiron.noctua.service.NoctuaBriefingService;
import com.kronos.chiron.push.service.WebPushService;
import com.kronos.chiron.utilisateur.model.AiProvider;
import com.kronos.chiron.utilisateur.model.Utilisateur;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class NoctuaBriefingServiceImpl implements NoctuaBriefingService {

    private static final int MEMORY_INJECTION_LIMIT = 10;
    private static final DateTimeFormatter HEURE = DateTimeFormatter.ofPattern("HH'h'mm", Locale.FRANCE);
    private static final Map<NoctuaBriefingType, String> TITRES_PUSH = Map.of(
            NoctuaBriefingType.REVEIL, "Réveil",
            NoctuaBriefingType.ACTIVITE, "Activité",
            NoctuaBriefingType.COUCHER, "Coucher");

    private final NoctuaBriefingRepository noctuaBriefingRepository;
    private final ConversationService conversationService;
    private final MemoryNoteService memoryNoteService;
    private final NoctuaAgentRouter noctuaAgentRouter;
    private final WebPushService webPushService;
    private final Clock clock;

    @Override
    public boolean dejaProduit(Utilisateur user, String cleDeclencheur) {
        return noctuaBriefingRepository.existsByUtilisateurAndCleDeclencheur(user, cleDeclencheur);
    }

    @Override
    public Optional<NoctuaBriefing> genererSiNecessaire(Utilisateur user, NoctuaBriefingType type,
            LocalDate dateReference, String cleDeclencheur, String titre, String declencheurLisible,
            String commandeSysteme) {
        if (dejaProduit(user, cleDeclencheur)) {
            return Optional.empty();
        }

        Conversation conversation = conversationService.getOrCreate(user, null, AgentType.NOCTUA);
        String memoryId = String.valueOf(conversation.getId());

        StringBuilder ctx = new StringBuilder();
        ctx.append("LANGUE DE RÉPONSE : réponds exclusivement en français.\n");
        ctx.append("SYSTEM CONTEXT - L'utilisateur est : ").append(user.getUsername()).append(".\n");
        ctx.append("DATE DU JOUR (AAAA-MM-JJ) : ").append(LocalDate.now(clock)).append(", heure actuelle ")
                .append(LocalDateTime.now(clock).format(HEURE)).append(".\n");
        ctx.append(memoryNoteService.formatForPrompt(user, MEMORY_INJECTION_LIMIT));
        ctx.append("COMMANDE SYSTEME : ").append(commandeSysteme);

        String reply;
        try {
            reply = noctuaAgentRouter.chatWithFallback(AiProvider.MISTRAL, memoryId, ctx.toString());
        } catch (AiUnavailableException e) {
            log.warn("NOCTUA_BRIEFING_ECHEC user={} type={} cle={} : {}", user.getUsername(), type, cleDeclencheur,
                    e.getMessage());
            return Optional.empty();
        }

        conversation.setTitre(titre);
        conversationService.recordExchange(conversation, declencheurLisible, reply);

        try {
            NoctuaBriefing briefing = noctuaBriefingRepository.save(NoctuaBriefing.builder()
                    .utilisateur(user)
                    .conversation(conversation)
                    .type(type)
                    .dateReference(dateReference)
                    .cleDeclencheur(cleDeclencheur)
                    .build());
            notifier(user, type, briefing, reply);
            return Optional.of(briefing);
        } catch (DataIntegrityViolationException e) {
            log.info("NOCTUA_BRIEFING_DOUBLON user={} cle={}", user.getUsername(), cleDeclencheur);
            return Optional.empty();
        }
    }

    private void notifier(Utilisateur user, NoctuaBriefingType type, NoctuaBriefing briefing, String reply) {
        try {
            webPushService.envoyerAuxAbonnements(user, TITRES_PUSH.get(type), reply, "/noctua/" + briefing.getId());
        } catch (RuntimeException e) {
            log.warn("NOCTUA_PUSH_ECHEC user={} briefingId={} : {}", user.getUsername(), briefing.getId(),
                    e.getMessage());
        }
    }
}
