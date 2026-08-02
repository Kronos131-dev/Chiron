package com.kronos.chiron.coach.service;

import com.kronos.chiron.utilisateur.model.AiProvider;
import com.kronos.chiron.utilisateur.model.Utilisateur;

public interface AiUsageService {

    int DAILY_GEMINI_LIMIT = 5;

    AiProvider resolveProvider(Utilisateur user);
}
