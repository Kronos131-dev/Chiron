package com.kronos.chiron.util;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.SecureRandom;
import java.util.Base64;

import static org.assertj.core.api.Assertions.*;

class TokenCipherServiceTest {

    private TokenCipherService cipherWithKey(String keyB64) {
        TokenCipherService svc = new TokenCipherService();
        ReflectionTestUtils.setField(svc, "configuredKeyB64", keyB64);
        svc.init();
        return svc;
    }

    private String randomKey() {
        byte[] k = new byte[32];
        new SecureRandom().nextBytes(k);
        return Base64.getEncoder().encodeToString(k);
    }

    @Test
    void encryptThenDecrypt_roundTrips() {
        TokenCipherService svc = cipherWithKey(randomKey());
        String plain = "fitbit-access-token-xyz";

        String encrypted = svc.encrypt(plain);

        assertThat(encrypted).isNotBlank().isNotEqualTo(plain);
        assertThat(svc.decrypt(encrypted)).isEqualTo(plain);
    }

    @Test
    void decryptWithDifferentKey_throws() {
        TokenCipherService a = cipherWithKey(randomKey());
        TokenCipherService b = cipherWithKey(randomKey());

        String encrypted = a.encrypt("secret");

        assertThatThrownBy(() -> b.decrypt(encrypted)).isInstanceOf(RuntimeException.class);
    }

    @Test
    void nullValues_passThrough() {
        TokenCipherService svc = cipherWithKey(randomKey());

        assertThat(svc.encrypt(null)).isNull();
        assertThat(svc.decrypt(null)).isNull();
    }

    @Test
    void invalidKeyLength_rejectedAtInit() {
        TokenCipherService svc = new TokenCipherService();
        ReflectionTestUtils.setField(svc, "configuredKeyB64",
                Base64.getEncoder().encodeToString(new byte[16]));

        assertThatThrownBy(svc::init).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void blankKey_fallsBackToEphemeralKey() {
        TokenCipherService svc = cipherWithKey("");

        String encrypted = svc.encrypt("data");
        assertThat(svc.decrypt(encrypted)).isEqualTo("data");
    }
}
