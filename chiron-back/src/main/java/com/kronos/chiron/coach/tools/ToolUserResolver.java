package com.kronos.chiron.coach.tools;

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

    public Utilisateur load(String userId) {
        return utilisateurRepository.findById(parseId(userId))
                .orElseThrow(() -> notFound("Utilisateur introuvable"));
    }

    private long parseId(String userId) {
        try {
            return Long.parseLong(userId);
        } catch (NumberFormatException e) {
            throw badRequest("Identifiant utilisateur invalide : " + userId, e);
        }
    }
}
