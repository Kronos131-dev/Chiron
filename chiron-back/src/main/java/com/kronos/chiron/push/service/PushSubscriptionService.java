package com.kronos.chiron.push.service;

import com.kronos.chiron.push.dto.PushSubscriptionRequestDto;
import com.kronos.chiron.utilisateur.model.Utilisateur;

public interface PushSubscriptionService {

    void enregistrer(Utilisateur utilisateur, PushSubscriptionRequestDto request);

    void desabonner(Utilisateur utilisateur, String endpoint);
}
