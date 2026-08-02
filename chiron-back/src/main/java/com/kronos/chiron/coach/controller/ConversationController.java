package com.kronos.chiron.coach.controller;

import com.kronos.chiron.coach.agent.ConversationMemoryManager;
import com.kronos.chiron.coach.dto.ConversationMessageDto;
import com.kronos.chiron.coach.dto.ConversationSummaryDto;
import com.kronos.chiron.utilisateur.model.Utilisateur;
import com.kronos.chiron.utilisateur.persistence.UtilisateurRepository;
import com.kronos.chiron.coach.service.ConversationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.NoSuchElementException;

/**
 * Gestion de l'historique des conversations du coach Chiron : liste, rechargement, suppression.
 * L'utilisateur est dérivé du principal JWT (jamais du corps de requête) et l'appartenance des
 * conversations est systématiquement vérifiée.
 */
@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;
    private final ConversationMemoryManager memoryManager;
    private final UtilisateurRepository utilisateurRepository;

    /** Liste les conversations de l'utilisateur, de la plus récente à la plus ancienne. */
    @GetMapping
    public List<ConversationSummaryDto> list(@AuthenticationPrincipal UserDetails userDetails) {
        Utilisateur user = currentUser(userDetails);
        return conversationService.listForUser(user).stream()
                .map(c -> new ConversationSummaryDto(c.getId(), c.getTitre(), c.getUpdatedAt()))
                .toList();
    }

    /** Messages d'une conversation (rechargement). */
    @GetMapping("/{id}/messages")
    public List<ConversationMessageDto> messages(@AuthenticationPrincipal UserDetails userDetails,
                                                 @PathVariable Long id) {
        Utilisateur user = currentUser(userDetails);
        return conversationService.getMessages(user, id).stream()
                .map(m -> new ConversationMessageDto(m.getRole().name(), m.getContent()))
                .toList();
    }

    /** Supprime une conversation et évince sa mémoire IA. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal UserDetails userDetails,
                                       @PathVariable Long id) {
        Utilisateur user = currentUser(userDetails);
        conversationService.delete(user, id);
        memoryManager.evict(String.valueOf(id));
        return ResponseEntity.noContent().build();
    }

    private Utilisateur currentUser(UserDetails userDetails) {
        return utilisateurRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new NoSuchElementException("Utilisateur introuvable"));
    }
}
