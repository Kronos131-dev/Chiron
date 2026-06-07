package com.kronos.chiron.controller;

import com.kronos.chiron.ai.ChironAgentRouter;
import com.kronos.chiron.ai.ConversationMemoryManager;
import com.kronos.chiron.dto.chat.ChatResponse;
import com.kronos.chiron.entity.AiProvider;
import com.kronos.chiron.entity.ChironMemoryNote;
import com.kronos.chiron.entity.Conversation;
import com.kronos.chiron.entity.Utilisateur;
import com.kronos.chiron.repository.UtilisateurRepository;
import com.kronos.chiron.service.AiUsageService;
import com.kronos.chiron.service.ConversationService;
import com.kronos.chiron.service.MemoryNoteService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for handling chat interactions with the AI coach.
 * Exposes endpoints for sending messages to the AI and explicitly ending a workout session.
 * Chaque échange est rattaché à une conversation persistée (la mémoire IA est indexée par
 * son id) ; le quota Gemini est appliqué avant chaque appel.
 */
@RestController
@RequestMapping("/api")
public class ChatController {

    private final ChironAgentRouter chironAgentRouter;
    private final UtilisateurRepository utilisateurRepository;
    private final MemoryNoteService memoryNoteService;
    private final ConversationService conversationService;
    private final ConversationMemoryManager memoryManager;
    private final AiUsageService aiUsageService;

    private static final int MEMORY_INJECTION_LIMIT = 10;

    public ChatController(ChironAgentRouter chironAgentRouter,
                          UtilisateurRepository utilisateurRepository,
                          MemoryNoteService memoryNoteService,
                          ConversationService conversationService,
                          ConversationMemoryManager memoryManager,
                          AiUsageService aiUsageService) {
        this.chironAgentRouter = chironAgentRouter;
        this.utilisateurRepository = utilisateurRepository;
        this.memoryNoteService = memoryNoteService;
        this.conversationService = conversationService;
        this.memoryManager = memoryManager;
        this.aiUsageService = aiUsageService;
    }

    /**
     * Data Transfer Object for chat requests.
     * {@code conversationId} est null pour démarrer une nouvelle conversation.
     */
    public static class ChatRequest {
        private String username;
        private String message;
        private Long conversationId;
        private String language; // 'fr' | 'en' ; null => fr

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public Long getConversationId() { return conversationId; }
        public void setConversationId(Long conversationId) { this.conversationId = conversationId; }
        public String getLanguage() { return language; }
        public void setLanguage(String language) { this.language = language; }
    }

    /** Directive de langue de réponse injectée en tête du contexte envoyé au modèle. */
    private static String languageDirective(String language) {
        boolean en = "en".equalsIgnoreCase(language);
        return en
                ? "LANGUE DE RÉPONSE : réponds exclusivement en anglais, quelle que soit la langue des données internes.\n"
                : "LANGUE DE RÉPONSE : réponds exclusivement en français.\n";
    }

    /**
     * Endpoint to send a standard chat message to the AI coach.
     * Injects systemic context regarding the user's role and identity before passing the message to the model.
     *
     * @param request The chat request containing the user's message, username and (optional) conversation id.
     * @return The AI's generated response and the conversation id it belongs to.
     */
    @PostMapping("/chat")
    public ChatResponse chat(@RequestBody ChatRequest request) {
        Utilisateur user = utilisateurRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));

        Conversation conversation = conversationService.getOrCreate(user, request.getConversationId());
        String memoryId = String.valueOf(conversation.getId());
        memoryManager.seedIfAbsent(memoryId, () -> conversationService.getMessages(conversation));

        StringBuilder ctx = new StringBuilder();
        ctx.append(languageDirective(request.getLanguage()));
        ctx.append("SYSTEM CONTEXT - L'utilisateur qui te parle est : ").append(user.getUsername())
                .append(". Son rôle est : ").append(user.getRole().name())
                .append(". S'il est ADMIN, il a le droit de demander des informations sur d'autres utilisateurs.\n");

        String memoryBlock = formatMemoryNotes(user);
        if (!memoryBlock.isEmpty()) {
            ctx.append(memoryBlock);
        }

        ctx.append("MESSAGE DE L'UTILISATEUR : ").append(request.getMessage());

        AiProvider provider = aiUsageService.resolveProvider(user);
        String reply = chironAgentRouter.chatWithFallback(provider, memoryId, ctx.toString());

        conversationService.recordExchange(conversation, request.getMessage(), reply);
        return new ChatResponse(conversation.getId(), reply);
    }

    private String formatMemoryNotes(Utilisateur user) {
        List<ChironMemoryNote> notes = memoryNoteService.getRecent(user, MEMORY_INJECTION_LIMIT);
        if (notes.isEmpty()) return "";
        StringBuilder sb = new StringBuilder("[MÉMOIRE LONG-TERME — notes durables, à utiliser sans les répéter] :\n");
        for (ChironMemoryNote n : notes) {
            sb.append("- #").append(n.getId()).append(" [").append(n.getType().name()).append("] ")
                    .append(n.getContent()).append("\n");
        }
        sb.append("\n");
        return sb.toString();
    }

    /**
     * Endpoint to explicitly signal the AI to end the current workout session.
     * Sends a system command to the AI instructing it to summarize the workout and persist the end state.
     *
     * @param request The request containing the username and (optional) conversation id.
     * @return The AI's generated closing response and the conversation id.
     */
    @PostMapping("/end-session")
    public ChatResponse endSession(@RequestBody ChatRequest request) {
        Utilisateur user = utilisateurRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));

        Conversation conversation = conversationService.getOrCreate(user, request.getConversationId());
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
