package com.kronos.chiron.push.service.impl;

import com.kronos.chiron.core.exceptions.ChironTechnicalException;

import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECPublicKeySpec;
import java.util.Arrays;
import java.util.Base64;

public final class EcKeys {

    private static final String CURVE = "secp256r1";
    private static final int COORDINATE_LENGTH_BYTES = 32;
    public static final int UNCOMPRESSED_POINT_LENGTH_BYTES = 65;

    private static final ECParameterSpec CURVE_PARAMS = ((ECPublicKey) generateKeyPair().getPublic()).getParams();

    private EcKeys() {
    }

    public static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec(CURVE));
            return generator.generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new ChironTechnicalException("Impossible de générer une paire de clés EC P-256", e);
        }
    }

    public static ECPublicKey publicKeyFromUncompressedPoint(byte[] raw) {
        if (raw.length != UNCOMPRESSED_POINT_LENGTH_BYTES || raw[0] != 0x04) {
            throw new ChironTechnicalException(
                    "Clé publique EC invalide : attendu un point non compressé de 65 octets commençant par 0x04");
        }
        BigInteger x = new BigInteger(1, Arrays.copyOfRange(raw, 1, 1 + COORDINATE_LENGTH_BYTES));
        BigInteger y = new BigInteger(1,
                Arrays.copyOfRange(raw, 1 + COORDINATE_LENGTH_BYTES, UNCOMPRESSED_POINT_LENGTH_BYTES));
        try {
            KeyFactory factory = KeyFactory.getInstance("EC");
            return (ECPublicKey) factory
                    .generatePublic(new ECPublicKeySpec(new ECPoint(x, y), CURVE_PARAMS));
        } catch (GeneralSecurityException e) {
            throw new ChironTechnicalException("Clé publique EC invalide", e);
        }
    }

    public static ECPrivateKey privateKeyFromRawScalar(byte[] raw32) {
        BigInteger s = new BigInteger(1, raw32);
        try {
            KeyFactory factory = KeyFactory.getInstance("EC");
            return (ECPrivateKey) factory.generatePrivate(new ECPrivateKeySpec(s, CURVE_PARAMS));
        } catch (GeneralSecurityException e) {
            throw new ChironTechnicalException("Clé privée EC invalide", e);
        }
    }

    public static byte[] uncompressedPointFromPublicKey(ECPublicKey key) {
        byte[] x = toFixedLength(key.getW().getAffineX(), COORDINATE_LENGTH_BYTES);
        byte[] y = toFixedLength(key.getW().getAffineY(), COORDINATE_LENGTH_BYTES);
        byte[] result = new byte[UNCOMPRESSED_POINT_LENGTH_BYTES];
        result[0] = 0x04;
        System.arraycopy(x, 0, result, 1, COORDINATE_LENGTH_BYTES);
        System.arraycopy(y, 0, result, 1 + COORDINATE_LENGTH_BYTES, COORDINATE_LENGTH_BYTES);
        return result;
    }

    public static byte[] rawScalarFromPrivateKey(ECPrivateKey key) {
        return toFixedLength(key.getS(), COORDINATE_LENGTH_BYTES);
    }

    public static String toBase64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static byte[] fromBase64Url(String value) {
        return Base64.getUrlDecoder().decode(value);
    }

    private static byte[] toFixedLength(BigInteger value, int length) {
        byte[] raw = value.toByteArray();
        if (raw.length == length) return raw;
        byte[] fixed = new byte[length];
        if (raw.length > length) {
            System.arraycopy(raw, raw.length - length, fixed, 0, length);
        } else {
            System.arraycopy(raw, 0, fixed, length - raw.length, raw.length);
        }
        return fixed;
    }

}
