package com.squad.backend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM encryption for sensitive data at rest (e.g. bank sort code, account number).
 * Encrypted values are stored with an "ENC:" prefix so plaintext can be detected for migration.
 * Key must be 32 bytes, base64-encoded, in app.encryption.key.
 */
@Service
@Slf4j
public class EncryptionService {

    private static final String PREFIX = "ENC:";
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;

    @Value("${app.encryption.key:}")
    private String base64Key;

    /**
     * Encrypts plaintext. Returns ENC:base64(iv+ciphertext) or original if key not set.
     */
    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isEmpty()) return plaintext;
        byte[] key = getKey();
        if (key == null) {
            log.warn("app.encryption.key is not set; storing payout details in plaintext. Set a 32-byte base64 key to enable encryption.");
            return plaintext;
        }

        try {
            SecureRandom random = new SecureRandom();
            byte[] iv = new byte[GCM_IV_LENGTH];
            random.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), spec);

            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            ByteBuffer buf = ByteBuffer.allocate(iv.length + ciphertext.length);
            buf.put(iv);
            buf.put(ciphertext);
            return PREFIX + Base64.getEncoder().encodeToString(buf.array());
        } catch (Exception e) {
            log.error("Encryption failed", e);
            throw new IllegalStateException("Encryption failed", e);
        }
    }

    /**
     * Decrypts if value starts with ENC:, otherwise returns as-is (backward compatibility).
     */
    public String decrypt(String value) {
        if (value == null || value.isEmpty()) return value;
        if (!value.startsWith(PREFIX)) return value; // stored as plaintext (e.g. before encryption was enabled)

        byte[] key = getKey();
        if (key == null) return value;

        try {
            String b64 = value.substring(PREFIX.length());
            byte[] combined = Base64.getDecoder().decode(b64);
            ByteBuffer buf = ByteBuffer.wrap(combined);
            byte[] iv = new byte[GCM_IV_LENGTH];
            buf.get(iv);
            byte[] ciphertext = new byte[buf.remaining()];
            buf.get(ciphertext);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), spec);
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("Decryption failed, returning value as-is: {}", e.getMessage());
            return value;
        }
    }

    private byte[] getKey() {
        if (base64Key == null || base64Key.isBlank()) return null;
        try {
            byte[] key = Base64.getDecoder().decode(base64Key.trim());
            if (key.length != 32) return null;
            return key;
        } catch (Exception e) {
            return null;
        }
    }
}
