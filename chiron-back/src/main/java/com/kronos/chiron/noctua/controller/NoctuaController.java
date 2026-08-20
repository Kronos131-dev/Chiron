package com.kronos.chiron.noctua.controller;

import static com.kronos.chiron.core.exceptions.ErrorFactory.notFound;

import com.kronos.chiron.coach.agent.ConversationMemoryManager;
import com.kronos.chiron.coach.dto.ChatResponse;
import com.kronos.chiron.coach.dto.ConversationMessageDto;
import com.kronos.chiron.coach.model.AgentType;
import com.kronos.chiron.coach.model.Conversation;
import com.kronos.chiron.coach.model.ConversationMessage;
import com.kronos.chiron.coach.service.AiUsageService;
import com.kronos.chiron.coach.service.ConversationService;
import com.kronos.chiron.coach.service.MemoryNoteService;
import com.kronos.chiron.core.security.AuthenticatedUserService;
import com.kronos.chiron.noctua.agent.NoctuaAgentRouter;
import com.kronos.chiron.noctua.dto.NoctuaBriefingDetailDto;
import com.kronos.chiron.noctua.dto.NoctuaBriefingDto;
import com.kronos.chiron.noctua.dto.NoctuaMessageRequest;
import com.kronos.chiron.noctua.dto.NonLusDto;
import com.kronos.chiron.noctua.model.NoctuaBriefing;
import com.kronos.chiron.noctua.persistence.NoctuaBriefingRepository;
import com.kronos.chiron.utilisateur.model.AiProvider;
import com.kronos.chiron.utilisateur.model.Utilisateur;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/noctua")
@RequiredArgsConstructor
public class NoctuaController {

    private static final int MEMORY_INJECTION_LIMIT = 10;

    private final AuthenticatedUserService authenticatedUserService;
    private final NoctuaBriefingRepository noctuaBriefingRepository;
    private final ConversationService conversationService;
    private final MemoryNoteService memoryNoteService;
    private final NoctuaAgentRouter noctuaAgentRouter;
    private final ConversationMemoryManager memoryManager;
    private final AiUsageService aiUsageService;
    private final Clock clock;

    @GetMapping("/briefings")
    public List<NoctuaBriefingDto> briefings(@RequestParam(defaultValue = "14") int jours) {
        Utilisateur user = authenticatedUserService.getAuthenticatedUser();
        LocalDateTime depuis = LocalDateTime.now(clock).minusDays(Math.max(1, jours));
        return noctuaBriefingRepository
                .findByUtilisateurAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(user, depuis).stream()
                .map(this::toDto)
                .toList();
    }

    @GetMapping("/briefings/{id}")
    public NoctuaBriefingDetailDto briefing(@PathVariable Long id) {
        Utilisateur user = authenticatedUserService.getAuthenticatedUser();
        NoctuaBriefing briefing = noctuaBriefingRepository.findByIdAndUtilisateur(id, user)
                .orElseThrow(() -> notFound("Briefing introuvable"));
        List<ConversationMessageDto> messages = conversationService.getMessages(briefing.getConversation()).stream()
                .map(m -> new ConversationMessageDto(m.getRole().name(), m.getContent()))
                .toList();
        return new NoctuaBriefingDetailDto(toDto(briefing), messages);
    }

    @GetMapping("/briefings/par-activite/{activiteId}")
    public NoctuaBriefingDto briefingParActivite(@PathVariable Long activiteId) {
        Utilisateur user = authenticatedUserService.getAuthenticatedUser();
        NoctuaBriefing briefing = noctuaBriefingRepository
                .findByUtilisateurAndCleDeclencheur(user, "ACTIVITE:" + activiteId)
                .orElseThrow(() -> notFound("Briefing introuvable"));
        return toDto(briefing);
    }

    @PostMapping("/briefings/{id}/messages")
    public ChatResponse envoyerMessage(@PathVariable Long id, @RequestBody NoctuaMessageRequest request) {
        Utilisateur user = authenticatedUserService.getAuthenticatedUser();
        NoctuaBriefing briefing = noctuaBriefingRepository.findByIdAndUtilisateur(id, user)
                .orElseThrow(() -> notFound("Briefing introuvable"));
        Conversation conversation = conversationService.getOrCreate(user, briefing.getConversation().getId(),
                AgentType.NOCTUA);
        String memoryId = String.valueOf(conversation.getId());
        memoryManager.seedIfAbsent(memoryId, () -> conversationService.getMessages(conversation));

        StringBuilder ctx = new StringBuilder();
        boolean en = "en".equalsIgnoreCase(request.language());
        ctx.append(en
                ? "LANGUE DE RÉPONSE : réponds exclusivement en anglais.\n"
                : "LANGUE DE RÉPONSE : réponds exclusivement en français.\n");
        ctx.append("SYSTEM CONTEXT - L'utilisateur qui te parle est : ").append(user.getUsername()).append(".\n");
        ctx.append(memoryNoteService.formatForPrompt(user, MEMORY_INJECTION_LIMIT));
        ctx.append("MESSAGE DE L'UTILISATEUR : ").append(request.message());

        AiProvider provider = aiUsageService.resolveProvider(user);
        String reply = noctuaAgentRouter.chatWithFallback(provider, memoryId, ctx.toString());

        conversationService.recordExchange(conversation, request.message(), reply);
        return new ChatResponse(conversation.getId(), reply);
    }

    @PostMapping("/briefings/{id}/lu")
    public ResponseEntity<Void> marquerLu(@PathVariable Long id) {
        Utilisateur user = authenticatedUserService.getAuthenticatedUser();
        NoctuaBriefing briefing = noctuaBriefingRepository.findByIdAndUtilisateur(id, user)
                .orElseThrow(() -> notFound("Briefing introuvable"));
        briefing.setLu(true);
        noctuaBriefingRepository.save(briefing);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/non-lus")
    public NonLusDto nonLus() {
        Utilisateur user = authenticatedUserService.getAuthenticatedUser();
        return new NonLusDto(noctuaBriefingRepository.countByUtilisateurAndLuFalse(user));
    }

    private NoctuaBriefingDto toDto(NoctuaBriefing briefing) {
        List<ConversationMessage> messages = conversationService.getMessages(briefing.getConversation());
        String premierParagraphe = messages.stream()
                .filter(m -> m.getRole().name().equals("AI"))
                .findFirst()
                .map(ConversationMessage::getContent)
                .orElse("");
        return new NoctuaBriefingDto(briefing.getId(), briefing.getType(), briefing.getDateReference(),
                briefing.getCreatedAt(), briefing.isLu(), premierParagraphe);
    }
}
