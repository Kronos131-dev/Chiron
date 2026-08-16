package com.kronos.chiron.coach.tools;

import com.kronos.chiron.coach.persistence.ConversationRepository;
import com.kronos.chiron.utilisateur.model.Utilisateur;
import com.kronos.chiron.utilisateur.persistence.UtilisateurRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import static com.kronos.chiron.core.exceptions.ErrorFactory.badRequest;
import static com.kronos.chiron.core.exceptions.ErrorFactory.notFound;

@Component
@RequiredArgsConstructor
public class ToolUserResolver {

    private final UtilisateurRepository utilisateurRepository;
    private final ConversationRepository conversationRepository;

    public Utilisateur load(String memoryId) {
        return utilisateurRepository.findById(ownerId(memoryId))
                .orElseThrow(() -> notFound("Utilisateur introuvable"));
    }

    public Long loadId(String memoryId) {
        return ownerId(memoryId);
    }

    private Long ownerId(String memoryId) {
        return conversationRepository.findOwnerId(parseId(memoryId))
                .orElseThrow(() -> notFound("Conversation introuvable : " + memoryId));
    }

    private long parseId(String memoryId) {
        try {
            return Long.parseLong(memoryId);
        } catch (NumberFormatException e) {
            throw badRequest("Identifiant de conversation invalide : " + memoryId, e);
        }
    }
}
