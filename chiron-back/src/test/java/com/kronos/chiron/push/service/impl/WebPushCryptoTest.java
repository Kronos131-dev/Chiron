package com.kronos.chiron.push.service.impl;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;

import static org.assertj.core.api.Assertions.assertThat;

// Given / When / Then : vecteur de test officiel du RFC 8291 §5 (A Worked Example), transcrit
// depuis https://www.rfc-editor.org/rfc/rfc8291.txt. Une correspondance octet pour octet de la
// sortie finale prouve simultanément l'accord ECDH, les trois étapes HKDF, l'assemblage de
// l'en-tête aes128gcm et le chiffrement AES-128-GCM : aucune de ces étapes ne peut être fausse
// sans faire diverger ce résultat.
class WebPushCryptoTest {

    private static final String UA_PUBLIC = "BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvT"
            + "BHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4";
    private static final String AUTH_SECRET = "BTBZMqHH6r4Tts7J_aSIgg";
    private static final String AS_PRIVATE = "yfWPiYE-n46HLnH0KqZOF1fJJU3MYrct3AELtAQ-oRw";
    private static final String AS_PUBLIC = "BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocIn"
            + "mYWAmS6TlzAC8wEqKK6PBru3jl7A8";
    private static final String SALT = "DGv6ra1nlYgDCS1FRnbzlw";
    private static final String PLAINTEXT = "When I grow up, I want to be a watermelon";
    private static final String EXPECTED_BODY = "DGv6ra1nlYgDCS1FRnbzlwAAEABBBP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27ml"
            + "mlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A_yl95bQpu6cVPT"
            + "pK4Mqgkf1CXztLVBSt2Ks3oZwbuwXPXLWyouBWLVWGNWQexSgSxsj_Qulcy4a-fN";

    @Test
    void encrypt_vecteurDeTestDuRfc8291_produitExactementLaSortieDuRfc() {
        ECPublicKey uaPublicKey = EcKeys.publicKeyFromUncompressedPoint(EcKeys.fromBase64Url(UA_PUBLIC));
        byte[] authSecret = EcKeys.fromBase64Url(AUTH_SECRET);
        byte[] salt = EcKeys.fromBase64Url(SALT);
        ECPrivateKey asPrivateKey = EcKeys.privateKeyFromRawScalar(EcKeys.fromBase64Url(AS_PRIVATE));
        ECPublicKey asPublicKey = EcKeys.publicKeyFromUncompressedPoint(EcKeys.fromBase64Url(AS_PUBLIC));
        KeyPair asKeyPair = new KeyPair(asPublicKey, asPrivateKey);

        byte[] body = WebPushCrypto.encrypt(uaPublicKey, authSecret, salt, asKeyPair,
                PLAINTEXT.getBytes(StandardCharsets.US_ASCII));

        assertThat(EcKeys.toBase64Url(body)).isEqualTo(EXPECTED_BODY);
    }
}
