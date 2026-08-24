package com.kronos.chiron.push.service.impl;

import com.kronos.chiron.core.exceptions.ChironTechnicalException;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;

@Component
@Slf4j
@Getter
public class VapidKeyProvider {

    @Value("${vapid.public-key:}")
    private String configuredPublicKey;

    @Value("${vapid.private-key:}")
    private String configuredPrivateKey;

    private ECPublicKey publicKey;
    private ECPrivateKey privateKey;
    private String publicKeyBase64Url;

    @PostConstruct
    void init() {
        if (configuredPublicKey.isBlank() || configuredPrivateKey.isBlank()) {
            KeyPair pair = EcKeys.generateKeyPair();
            publicKey = (ECPublicKey) pair.getPublic();
            privateKey = (ECPrivateKey) pair.getPrivate();
            publicKeyBase64Url = EcKeys.toBase64Url(EcKeys.uncompressedPointFromPublicKey(publicKey));
            log.warn("VAPID_PUBLIC_KEY/VAPID_PRIVATE_KEY absentes : paire éphémère générée, invalide après"
                    + " redémarrage (les abonnements existants devront se réabonner). À éviter en production."
                    + " VAPID_PUBLIC_KEY={} VAPID_PRIVATE_KEY={}", publicKeyBase64Url,
                    EcKeys.toBase64Url(EcKeys.rawScalarFromPrivateKey(privateKey)));
            return;
        }
        publicKey = EcKeys.publicKeyFromUncompressedPoint(EcKeys.fromBase64Url(configuredPublicKey));
        privateKey = EcKeys.privateKeyFromRawScalar(EcKeys.fromBase64Url(configuredPrivateKey));
        publicKeyBase64Url = configuredPublicKey;
        if (!publicKeyIsConsistentWithPrivateKey()) {
            throw new ChironTechnicalException(
                    "VAPID_PUBLIC_KEY et VAPID_PRIVATE_KEY ne forment pas une paire de clés cohérente");
        }
    }

    // WHY: la JCE n'expose aucun moyen direct de vérifier qu'un ECPublicKey et un ECPrivateKey
    // forment la même paire (pas de multiplication de point exposée). Signer un message
    // arbitraire avec la clé privée puis le vérifier avec la clé publique le prouve
    // indirectement : la vérification échoue si les deux clés ne correspondent pas.
    private boolean publicKeyIsConsistentWithPrivateKey() {
        try {
            byte[] probe = "chiron-vapid-key-check".getBytes(StandardCharsets.UTF_8);
            Signature signer = Signature.getInstance("SHA256withECDSA");
            signer.initSign(privateKey);
            signer.update(probe);
            byte[] signature = signer.sign();

            Signature verifier = Signature.getInstance("SHA256withECDSA");
            verifier.initVerify(publicKey);
            verifier.update(probe);
            return verifier.verify(signature);
        } catch (GeneralSecurityException e) {
            throw new ChironTechnicalException("Impossible de vérifier la cohérence de la paire VAPID", e);
        }
    }
}
