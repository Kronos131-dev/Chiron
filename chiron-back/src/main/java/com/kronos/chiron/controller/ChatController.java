package com.kronos.chiron.controller;

import com.kronos.chiron.ai.ChironAgent;
import com.kronos.chiron.entity.Role;
import com.kronos.chiron.entity.Utilisateur;
import com.kronos.chiron.repository.UtilisateurRepository;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for handling chat interactions with the AI coach.
 * Exposes endpoints for sending messages to the AI and explicitly ending a workout session.
 */
@RestController
@RequestMapping("/api")
public class ChatController {

    private final ChironAgent chironAgent;
    private final UtilisateurRepository utilisateurRepository;

    /**
     * Constructs a new ChatController.
     *
     * @param chironAgent           The AI agent interface.
     * @param utilisateurRepository The repository to fetch user details.
     */
    public ChatController(ChironAgent chironAgent, UtilisateurRepository utilisateurRepository) {
        this.chironAgent = chironAgent;
        this.utilisateurRepository = utilisateurRepository;
    }

    /**
     * Data Transfer Object for chat requests.
     */
    public static class ChatRequest {
        private String username;
        private String message;

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }

    /**
     * Endpoint to send a standard chat message to the AI coach.
     * Injects systemic context regarding the user's role and identity before passing the message to the model.
     *
     * @param request The chat request containing the user's message and username.
     * @return The AI's generated response string.
     */
    @PostMapping("/chat")
    public String chat(@RequestBody ChatRequest request) {
        Utilisateur user = utilisateurRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));

        String contextMessage = "SYSTEM CONTEXT - L'utilisateur qui te parle est : " + user.getUsername() 
                              + ". Son rôle est : " + user.getRole().name() 
                              + ". S'il est ADMIN, il a le droit de demander des informations sur d'autres utilisateurs.\n"
                              + "MESSAGE DE L'UTILISATEUR : " + request.getMessage();

        return chironAgent.chat(user.getId().toString(), contextMessage);
    }

    /**
     * Endpoint to explicitly signal the AI to end the current workout session.
     * Sends a system command to the AI instructing it to summarize the workout and persist the end state.
     *
     * @param request The request containing the username terminating the session.
     * @return The AI's generated closing response string.
     */
    @PostMapping("/end-session")
    public String endSession(@RequestBody ChatRequest request) {
        Utilisateur user = utilisateurRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));

        return chironAgent.chat(user.getId().toString(),
                "COMMANDE SYSTEME : L'utilisateur vient de cliquer sur 'Terminer l'entraînement'. Enregistre la fin de la séance dans la base de données, fais un résumé très court et martial de ses efforts, et dis-lui d'aller se reposer.");
    }
}
