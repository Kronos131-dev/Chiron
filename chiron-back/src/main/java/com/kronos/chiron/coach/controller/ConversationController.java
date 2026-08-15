package com.kronos.chiron.coach.controller;

import static com.kronos.chiron.core.exceptions.ErrorFactory.notFound;

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

@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;
    private final ConversationMemoryManager memoryManager;
    private final UtilisateurRepository utilisateurRepository;

    @GetMapping
    public List<ConversationSummaryDto> list(@AuthenticationPrincipal UserDetails userDetails) {
        Utilisateur user = currentUser(userDetails);
        return conversationService.listForUser(user).stream()
                .map(c -> new ConversationSummaryDto(c.getId(), c.getTitre(), c.getUpdatedAt()))
                .toList();
    }

    @GetMapping("/{id}/messages")
    public List<ConversationMessageDto> messages(@AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id) {
        Utilisateur user = currentUser(userDetails);
        return conversationService.getMessages(user, id).stream()
                .map(m -> new ConversationMessageDto(m.getRole().name(), m.getContent()))
                .toList();
    }

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
                .orElseThrow(() -> notFound("Utilisateur introuvable"));
    }
}
