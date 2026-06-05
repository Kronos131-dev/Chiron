package com.kronos.chiron.service;

import com.kronos.chiron.entity.AiProvider;
import com.kronos.chiron.entity.Role;
import com.kronos.chiron.entity.Utilisateur;
import com.kronos.chiron.repository.UtilisateurRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.NoSuchElementException;

/**
 * Applique le quota journalier Gemini. Les non-admins sont limités à {@value #DAILY_GEMINI_LIMIT}
 * requêtes Gemini par jour ; au-delà, le coach bascule silencieusement sur Mistral. Les admins ne
 * sont pas comptés. Chaque requête Gemini effectivement servie incrémente le compteur du jour.
 */
@Service
@RequiredArgsConstructor
public class AiUsageService {

    public static final int DAILY_GEMINI_LIMIT = 5;

    private final UtilisateurRepository utilisateurRepository;

    /**
     * Détermine le fournisseur effectif pour cette requête et enregistre la consommation Gemini.
     * Renvoie {@link AiProvider#GEMINI} si l'utilisateur le demande et reste sous le quota (ou est
     * admin), sinon {@link AiProvider#MISTRAL}.
     */
    @Transactional
    public AiProvider resolveProvider(Utilisateur user) {
        if (user.getAiProvider() != AiProvider.GEMINI) {
            return AiProvider.MISTRAL;
        }
        if (user.getRole() == Role.ADMIN) {
            return AiProvider.GEMINI;
        }

        Utilisateur managed = utilisateurRepository.findById(user.getId())
                .orElseThrow(() -> new NoSuchElementException("Utilisateur introuvable"));

        LocalDate today = LocalDate.now();
        if (!today.equals(managed.getGeminiCallDate())) {
            managed.setGeminiCallDate(today);
            managed.setGeminiCallCount(0);
        }

        if (managed.getGeminiCallCount() >= DAILY_GEMINI_LIMIT) {
            return AiProvider.MISTRAL;
        }

        managed.setGeminiCallCount(managed.getGeminiCallCount() + 1);
        utilisateurRepository.save(managed);
        return AiProvider.GEMINI;
    }
}
