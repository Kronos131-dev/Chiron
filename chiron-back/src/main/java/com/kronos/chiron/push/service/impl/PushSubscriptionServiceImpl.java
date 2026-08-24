package com.kronos.chiron.push.service.impl;

import com.kronos.chiron.push.dto.PushSubscriptionRequestDto;
import com.kronos.chiron.push.model.PushSubscription;
import com.kronos.chiron.push.persistence.PushSubscriptionRepository;
import com.kronos.chiron.push.service.PushSubscriptionService;
import com.kronos.chiron.utilisateur.model.Utilisateur;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PushSubscriptionServiceImpl implements PushSubscriptionService {

    private final PushSubscriptionRepository pushSubscriptionRepository;

    @Override
    @Transactional
    public void enregistrer(Utilisateur utilisateur, PushSubscriptionRequestDto request) {
        PushSubscription abonnement = pushSubscriptionRepository.findByEndpoint(request.endpoint())
                .orElseGet(() -> PushSubscription.builder().endpoint(request.endpoint()).build());
        abonnement.setUtilisateur(utilisateur);
        abonnement.setCleP256dh(request.keys().p256dh());
        abonnement.setCleAuth(request.keys().auth());
        pushSubscriptionRepository.save(abonnement);
    }

    @Override
    @Transactional
    public void desabonner(Utilisateur utilisateur, String endpoint) {
        pushSubscriptionRepository.deleteByUtilisateurAndEndpoint(utilisateur, endpoint);
    }
}
