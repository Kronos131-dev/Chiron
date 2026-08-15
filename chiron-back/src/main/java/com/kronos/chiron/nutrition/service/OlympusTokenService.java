package com.kronos.chiron.nutrition.service;

public interface OlympusTokenService {

    String encrypt(String plaintext);

    String decrypt(String ciphertextB64);
}
