package com.kronos.chiron.push.service.impl;

import com.kronos.chiron.core.exceptions.ChironTechnicalException;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;

// WHY: RFC 8291 (chiffrement des messages) et RFC 8188 (Content-Encoding: aes128gcm) ne sont
// implémentés par aucune bibliothèque standard du JDK ; ce module suit leur pseudocode §3.4
// littéralement et est vérifié bit à bit contre le vecteur de test officiel du RFC 8291 §5
// (WebPushCryptoTest).
public final class WebPushCrypto {

    private static final byte[] KEY_INFO_PREFIX = "WebPush: info\0".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] CEK_INFO = "Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] NONCE_INFO = "Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII);

    private static final int CEK_LENGTH_BYTES = 16;
    private static final int NONCE_LENGTH_BYTES = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final byte LAST_RECORD_DELIMITER = 0x02;

    public static final int RECORD_SIZE = 4096;

    private WebPushCrypto() {
    }

    public static byte[] encrypt(ECPublicKey uaPublicKey, byte[] authSecret, byte[] salt, KeyPair asKeyPair,
            byte[] plaintext) {
        byte[] uaPublicRaw = EcKeys.uncompressedPointFromPublicKey(uaPublicKey);
        byte[] asPublicRaw = EcKeys.uncompressedPointFromPublicKey((ECPublicKey) asKeyPair.getPublic());

        byte[] ecdhSecret = ecdh((ECPrivateKey) asKeyPair.getPrivate(), uaPublicKey);
        byte[] prkKey = hmacSha256(authSecret, ecdhSecret);

        byte[] keyInfo = concat(KEY_INFO_PREFIX, uaPublicRaw, asPublicRaw);
        byte[] ikm = hmacSha256(prkKey, concat(keyInfo, new byte[]{0x01}));

        byte[] prk = hmacSha256(salt, ikm);
        byte[] cek = truncate(hmacSha256(prk, concat(CEK_INFO, new byte[]{0x01})), CEK_LENGTH_BYTES);
        byte[] nonce = truncate(hmacSha256(prk, concat(NONCE_INFO, new byte[]{0x01})), NONCE_LENGTH_BYTES);

        byte[] paddedPlaintext = concat(plaintext, new byte[]{LAST_RECORD_DELIMITER});
        byte[] ciphertext = aesGcmEncrypt(cek, nonce, paddedPlaintext);

        return concat(header(salt, asPublicRaw), ciphertext);
    }

    private static byte[] header(byte[] salt, byte[] asPublicRaw) {
        ByteBuffer buffer = ByteBuffer.allocate(salt.length + 4 + 1 + asPublicRaw.length);
        buffer.put(salt);
        buffer.putInt(RECORD_SIZE);
        buffer.put((byte) asPublicRaw.length);
        buffer.put(asPublicRaw);
        return buffer.array();
    }

    private static byte[] ecdh(ECPrivateKey privateKey, ECPublicKey publicKey) {
        try {
            KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
            agreement.init(privateKey);
            agreement.doPhase(publicKey, true);
            return agreement.generateSecret();
        } catch (GeneralSecurityException e) {
            throw new ChironTechnicalException("Échec de l'accord de clé ECDH pour le chiffrement Web Push", e);
        }
    }

    private static byte[] aesGcmEncrypt(byte[] key, byte[] nonce, byte[] plaintext) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce));
            return cipher.doFinal(plaintext);
        } catch (GeneralSecurityException e) {
            throw new ChironTechnicalException("Échec du chiffrement AES-128-GCM du message Web Push", e);
        }
    }

    private static byte[] hmacSha256(byte[] key, byte[] message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(message);
        } catch (GeneralSecurityException e) {
            throw new ChironTechnicalException("Échec HMAC-SHA-256 durant la dérivation de clé Web Push", e);
        }
    }

    private static byte[] truncate(byte[] bytes, int length) {
        byte[] result = new byte[length];
        System.arraycopy(bytes, 0, result, 0, length);
        return result;
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            out.writeBytes(part);
        }
        return out.toByteArray();
    }
}
