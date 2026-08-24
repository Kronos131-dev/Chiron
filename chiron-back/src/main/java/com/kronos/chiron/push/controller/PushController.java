package com.kronos.chiron.push.controller;

import com.kronos.chiron.core.security.AuthenticatedUserService;
import com.kronos.chiron.push.dto.PushSubscriptionRequestDto;
import com.kronos.chiron.push.dto.PushUnsubscribeRequestDto;
import com.kronos.chiron.push.dto.VapidPublicKeyDto;
import com.kronos.chiron.push.service.PushSubscriptionService;
import com.kronos.chiron.push.service.impl.VapidKeyProvider;
import com.kronos.chiron.utilisateur.model.Utilisateur;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/push")
@RequiredArgsConstructor
public class PushController {

    private final AuthenticatedUserService authenticatedUserService;
    private final PushSubscriptionService pushSubscriptionService;
    private final VapidKeyProvider vapidKeyProvider;

    @GetMapping("/cle-publique")
    public VapidPublicKeyDto clePublique() {
        return new VapidPublicKeyDto(vapidKeyProvider.getPublicKeyBase64Url());
    }

    @PostMapping("/abonnement")
    public ResponseEntity<Void> abonner(@RequestBody PushSubscriptionRequestDto request) {
        Utilisateur user = authenticatedUserService.getAuthenticatedUser();
        pushSubscriptionService.enregistrer(user, request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/abonnement")
    public ResponseEntity<Void> desabonner(@RequestBody PushUnsubscribeRequestDto request) {
        Utilisateur user = authenticatedUserService.getAuthenticatedUser();
        pushSubscriptionService.desabonner(user, request.endpoint());
        return ResponseEntity.noContent().build();
    }
}
