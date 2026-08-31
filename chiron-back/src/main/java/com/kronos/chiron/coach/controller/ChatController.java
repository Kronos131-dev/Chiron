package com.kronos.chiron.coach.controller;

import static com.kronos.chiron.core.exceptions.ErrorFactory.notFound;

import com.kronos.chiron.coach.agent.ChironAgentRouter;
import com.kronos.chiron.coach.agent.ConversationMemoryManager;
import com.kronos.chiron.coach.context.VoiceSessionContext;
import com.kronos.chiron.coach.dto.ChatResponse;
import com.kronos.chiron.utilisateur.model.AiProvider;
import com.kronos.chiron.coach.model.AgentType;
import com.kronos.chiron.coach.model.Conversation;
import com.kronos.chiron.utilisateur.model.Utilisateur;
import com.kronos.chiron.utilisateur.persistence.UtilisateurRepository;
import com.kronos.chiron.coach.service.AiUsageService;
import com.kronos.chiron.coach.service.ConversationService;
import com.kronos.chiron.coach.service.MemoryNoteService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class ChatController {

    private final ChironAgentRouter chironAgentRouter;
    private final UtilisateurRepository utilisateurRepository;
    private final MemoryNoteService memoryNoteService;
    private final ConversationService conversationService;
    private final ConversationMemoryManager memoryManager;
    private final AiUsageService aiUsageService;
    private final VoiceSessionContext voiceSessionContext;

    private static final int MEMORY_INJECTION_LIMIT = 10;

    public ChatController(ChironAgentRouter chironAgentRouter,
            UtilisateurRepository utilisateurRepository,
            MemoryNoteService memoryNoteService,
            ConversationService conversationService,
            ConversationMemoryManager memoryManager,
            AiUsageService aiUsageService,
            VoiceSessionContext voiceSessionContext) {
        this.chironAgentRouter = chironAgentRouter;
        this.utilisateurRepository = utilisateurRepository;
        this.memoryNoteService = memoryNoteService;
        this.conversationService = conversationService;
        this.memoryManager = memoryManager;
        this.aiUsageService = aiUsageService;
        this.voiceSessionContext = voiceSessionContext;
    }

    public static class ChatRequest {
        private String message;
        private Long conversationId;
        private String language;
        private Long seanceId;

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public Long getConversationId() {
            return conversationId;
        }

        public void setConversationId(Long conversationId) {
            this.conversationId = conversationId;
        }

        public String getLanguage() {
            return language;
        }

        public void setLanguage(String language) {
            this.language = language;
        }

        public Long getSeanceId() {
            return seanceId;
        }

        public void setSeanceId(Long seanceId) {
            this.seanceId = seanceId;
        }
    }

    private static String languageDirective(String language) {
        boolean en = "en".equalsIgnoreCase(language);
        return en
                ? "LANGUE DE RÉPONSE : réponds exclusivement en anglais, quelle que soit la langue des données internes.\n"
                : "LANGUE DE RÉPONSE : réponds exclusivement en français.\n";
    }

    @PostMapping("/chat")
    public ChatResponse chat(@AuthenticationPrincipal UserDetails principal, @RequestBody ChatRequest request) {
        Utilisateur user = utilisateurRepository.findByUsername(principal.getUsername())
                .orElseThrow(() -> notFound("User not found"));

        voiceSessionContext.setPinnedSeanceId(request.getSeanceId());

        Conversation conversation = conversationService.getOrCreate(user, request.getConversationId(),
                AgentType.CHIRON);
        String memoryId = String.valueOf(conversation.getId());
        memoryManager.seedIfAbsent(memoryId, () -> conversationService.getMessages(conversation));

        StringBuilder ctx = new StringBuilder();
        ctx.append(languageDirective(request.getLanguage()));
        ctx.append("SYSTEM CONTEXT - L'utilisateur qui te parle est : ").append(user.getUsername())
                .append(". Son rôle est : ").append(user.getRole().name())
                .append(". S'il est ADMIN, il a le droit de demander des informations sur d'autres utilisateurs.\n");

        ctx.append(memoryNoteService.formatForPrompt(user, MEMORY_INJECTION_LIMIT));

        ctx.append("MESSAGE DE L'UTILISATEUR : ").append(request.getMessage());

        AiProvider provider = aiUsageService.resolveProvider(user);
        String reply = chironAgentRouter.chatWithFallback(provider, memoryId, ctx.toString());

        conversationService.recordExchange(conversation, request.getMessage(), reply);
        return new ChatResponse(conversation.getId(), reply);
    }

    @PostMapping("/end-session")
    public ChatResponse endSession(@AuthenticationPrincipal UserDetails principal, @RequestBody ChatRequest request) {
        Utilisateur user = utilisateurRepository.findByUsername(principal.getUsername())
                .orElseThrow(() -> notFound("User not found"));

        Conversation conversation = conversationService.getOrCreate(user, request.getConversationId(),
                AgentType.CHIRON);
        String memoryId = String.valueOf(conversation.getId());
        memoryManager.seedIfAbsent(memoryId, () -> conversationService.getMessages(conversation));

        AiProvider provider = aiUsageService.resolveProvider(user);
        String reply = chironAgentRouter.chatWithFallback(provider, memoryId,
                languageDirective(request.getLanguage())
                        + "COMMANDE SYSTEME : L'utilisateur vient de cliquer sur 'Terminer l'entraînement'. Enregistre la fin de la séance dans la base de données, fais un résumé très court et martial de ses efforts, et dis-lui d'aller se reposer.");

        conversationService.recordExchange(conversation, "Terminer l'entraînement", reply);
        return new ChatResponse(conversation.getId(), reply);
    }
}
