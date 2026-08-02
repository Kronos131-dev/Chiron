package com.kronos.chiron.coach.service.impl;

import com.kronos.chiron.coach.service.AiUsageService;

import java.time.Clock;

import com.kronos.chiron.utilisateur.model.AiProvider;
import com.kronos.chiron.utilisateur.model.Role;
import com.kronos.chiron.utilisateur.model.Utilisateur;
import com.kronos.chiron.utilisateur.persistence.UtilisateurRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class AiUsageServiceImpl implements AiUsageService {

    private final UtilisateurRepository utilisateurRepository;

    private final Clock clock;
    @Transactional
    @Override
    public AiProvider resolveProvider(Utilisateur user) {
        if (user.getAiProvider() != AiProvider.GEMINI) {
            return AiProvider.MISTRAL;
        }
        if (user.getRole() == Role.ADMIN) {
            return AiProvider.GEMINI;
        }

        Utilisateur managed = utilisateurRepository.findById(user.getId())
                .orElseThrow(() -> new NoSuchElementException("Utilisateur introuvable"));

        LocalDate today = LocalDate.now(clock);
        if (!today.equals(managed.getGeminiCallDate())) {
            managed.setGeminiCallDate(today);
            managed.setGeminiCallCount(0);
        }

        if (managed.getGeminiCallCount() >= AiUsageService.DAILY_GEMINI_LIMIT) {
            return AiProvider.MISTRAL;
        }

        managed.setGeminiCallCount(managed.getGeminiCallCount() + 1);
        utilisateurRepository.save(managed);
        return AiProvider.GEMINI;
    }
}
