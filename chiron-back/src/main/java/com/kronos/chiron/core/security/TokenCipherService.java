package com.kronos.chiron.core.security;

import com.kronos.chiron.core.exceptions.ChironTechnicalException;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Base64;

@Service
@Slf4j
public class TokenCipherService {

    private static final String ALG = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;

    @Value("${chiron.secret-key:}")
    private String configuredKeyB64;

    private SecretKey secretKey;
    private final SecureRandom random = new SecureRandom();

    @PostConstruct
    void init() {
        if (configuredKeyB64 == null || configuredKeyB64.isBlank()) {
            byte[] keyBytes = new byte[32];
            random.nextBytes(keyBytes);
            this.secretKey = new SecretKeySpec(keyBytes, ALG);
            log.warn("CHIRON_SECRET_KEY non fournie : clé éphémère générée. Les tokens chiffrés "
                    + "persistés ne seront plus déchiffrables après redémarrage. À éviter en production.");
            return;
        }
        byte[] keyBytes = Base64.getDecoder().decode(configuredKeyB64);
        if (keyBytes.length != 32) {
            throw new IllegalStateException("chiron.secret-key doit faire exactement 32 octets (256 bits) base64.");
        }
        this.secretKey = new SecretKeySpec(keyBytes, ALG);
    }

    public String encrypt(String plaintext) {
        if (plaintext == null) return null;
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] cipherBytes = cipher.doFinal(plaintext.getBytes());

            ByteBuffer buf = ByteBuffer.allocate(iv.length + cipherBytes.length);
            buf.put(iv);
            buf.put(cipherBytes);
            return Base64.getEncoder().encodeToString(buf.array());
        } catch (Exception e) {
            throw new ChironTechnicalException("Échec du chiffrement du token", e);
        }
    }

    public String decrypt(String ciphertextB64) {
        if (ciphertextB64 == null) return null;
        try {
            byte[] all = Base64.getDecoder().decode(ciphertextB64);
            byte[] iv = new byte[IV_LENGTH];
            byte[] cipherBytes = new byte[all.length - IV_LENGTH];
            System.arraycopy(all, 0, iv, 0, IV_LENGTH);
            System.arraycopy(all, IV_LENGTH, cipherBytes, 0, cipherBytes.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(cipherBytes));
        } catch (Exception e) {
            throw new ChironTechnicalException("Échec du déchiffrement du token (clé changée ou donnée corrompue ?)", e);
        }
    }
}
