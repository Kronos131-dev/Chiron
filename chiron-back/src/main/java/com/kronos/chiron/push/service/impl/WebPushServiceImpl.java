package com.kronos.chiron.push.service.impl;

import com.kronos.chiron.push.model.PushSubscription;
import com.kronos.chiron.push.persistence.PushSubscriptionRepository;
import com.kronos.chiron.push.service.WebPushService;
import com.kronos.chiron.utilisateur.model.Utilisateur;
import io.jsonwebtoken.Jwts;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.security.KeyPair;
import java.security.SecureRandom;
import java.security.interfaces.ECPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class WebPushServiceImpl implements WebPushService {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String TTL_SECONDES = String.valueOf(Duration.ofHours(24).toSeconds());
    private static final Duration JWT_EXPIRATION = Duration.ofHours(12);
    private static final int SALT_LENGTH_BYTES = 16;

    private final PushSubscriptionRepository pushSubscriptionRepository;
    private final VapidKeyProvider vapidKeyProvider;
    private final RestClient restClient;
    private final Clock clock;
    private final String vapidSubject;
    private final SecureRandom random = new SecureRandom();

    public WebPushServiceImpl(PushSubscriptionRepository pushSubscriptionRepository,
            VapidKeyProvider vapidKeyProvider, RestClient.Builder restClientBuilder, Clock clock,
            @Value("${vapid.subject:mailto:contact@chiron-sanctuaire.fr}") String vapidSubject) {
        this.pushSubscriptionRepository = pushSubscriptionRepository;
        this.vapidKeyProvider = vapidKeyProvider;
        this.restClient = restClientBuilder.build();
        this.clock = clock;
        this.vapidSubject = vapidSubject;
    }

    @Override
    public void envoyerAuxAbonnements(Utilisateur utilisateur, String titre, String corps, String url) {
        List<PushSubscription> abonnements = pushSubscriptionRepository.findByUtilisateur(utilisateur);
        byte[] plaintext = payload(titre, corps, url);
        for (PushSubscription abonnement : abonnements) {
            try {
                envoyer(abonnement, plaintext);
            } catch (PushSubscriptionExpiredException e) {
                pushSubscriptionRepository.delete(abonnement);
                log.info("PUSH_ABONNEMENT_EXPIRE user={} id={}", utilisateur.getUsername(), abonnement.getId());
            } catch (RuntimeException e) {
                log.warn("PUSH_ECHEC user={} id={} : {}", utilisateur.getUsername(), abonnement.getId(),
                        e.getMessage());
            }
        }
    }

    private byte[] payload(String titre, String corps, String url) {
        Map<String, Object> notification = Map.of(
                "title", titre,
                "body", corps,
                "data", Map.of("onActionClick",
                        Map.of("default", Map.of("operation", "navigateLastFocusedOrOpen", "url", url))));
        return JSON.writeValueAsBytes(Map.of("notification", notification));
    }

    private void envoyer(PushSubscription abonnement, byte[] plaintext) {
        ECPublicKey uaPublicKey = EcKeys
                .publicKeyFromUncompressedPoint(EcKeys.fromBase64Url(abonnement.getCleP256dh()));
        byte[] authSecret = EcKeys.fromBase64Url(abonnement.getCleAuth());
        byte[] salt = randomSalt();
        KeyPair asKeyPair = EcKeys.generateKeyPair();
        byte[] body = WebPushCrypto.encrypt(uaPublicKey, authSecret, salt, asKeyPair, plaintext);

        try {
            restClient.post()
                    .uri(URI.create(abonnement.getEndpoint()))
                    .header(HttpHeaders.CONTENT_ENCODING, "aes128gcm")
                    .header(HttpHeaders.AUTHORIZATION, vapidAuthorization(abonnement.getEndpoint()))
                    .header("TTL", TTL_SECONDES)
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException e) {
            HttpStatusCode status = e.getStatusCode();
            if (status.value() == 404 || status.value() == 410) {
                throw new PushSubscriptionExpiredException(
                        "Le service de push répond " + status.value() + " : abonnement mort");
            }
            throw e;
        } catch (ResourceAccessException e) {
            log.warn("Service de push injoignable ({}) : {}", abonnement.getEndpoint(), e.getMessage());
        }
    }

    private String vapidAuthorization(String endpoint) {
        Instant now = clock.instant();
        String jwt = Jwts.builder()
                .setAudience(origin(endpoint))
                .subject(vapidSubject)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(JWT_EXPIRATION)))
                .signWith(vapidKeyProvider.getPrivateKey(), Jwts.SIG.ES256)
                .compact();
        return "vapid t=" + jwt + ", k=" + vapidKeyProvider.getPublicKeyBase64Url();
    }

    private String origin(String endpoint) {
        URI uri = URI.create(endpoint);
        String origin = uri.getScheme() + "://" + uri.getHost();
        return uri.getPort() == -1 ? origin : origin + ":" + uri.getPort();
    }

    private byte[] randomSalt() {
        byte[] salt = new byte[SALT_LENGTH_BYTES];
        random.nextBytes(salt);
        return salt;
    }
}
