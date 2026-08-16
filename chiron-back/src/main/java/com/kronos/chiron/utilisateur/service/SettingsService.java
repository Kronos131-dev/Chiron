package com.kronos.chiron.utilisateur.service;

import com.kronos.chiron.utilisateur.dto.AiProviderDto;
import com.kronos.chiron.utilisateur.model.AiProvider;

public interface SettingsService {

    com.kronos.chiron.utilisateur.dto.TrainingPrefsDto getTrainingPrefs(String username);

    void updateTrainingPrefs(String username, boolean halteresParImplement, boolean machineParCote);

    AiProviderDto getAiProvider(String username);

    void updateAiProvider(String username, AiProvider provider);

    void changePassword(String username, String currentPassword, String newPassword);

    void changeEmail(String username, String newEmail);

    void changeIdentity(String username, String prenom, String nom);

    String changeUsername(String username, String newUsername);

    void forgotPassword(String email);

    void resetPassword(String tokenValue, String newPassword);
}
