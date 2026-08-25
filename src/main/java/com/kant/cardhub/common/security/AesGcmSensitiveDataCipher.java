package com.kant.cardhub.common.security;

import com.kant.cardhub.common.config.SecurityDataProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@Component
@ConditionalOnProperty(name = "cardhub.security.cipher-provider", havingValue = "aes", matchIfMissing = true)
public class AesGcmSensitiveDataCipher implements SensitiveDataCipher {

    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private final SecretKeySpec key;
    private final String keyVersion;
    private final SecureRandom secureRandom = new SecureRandom();

    public AesGcmSensitiveDataCipher(SecurityDataProperties properties) {
        if (properties.piiKey() == null || properties.piiKey().isBlank()) {
            throw new IllegalStateException("PII_ENCRYPTION_KEY is required when CIPHER_PROVIDER=aes");
        }
        if (properties.piiKeyVersion() == null || properties.piiKeyVersion().isBlank()) {
            throw new IllegalStateException("PII_KEY_VERSION is required when CIPHER_PROVIDER=aes");
        }
        this.key = new SecretKeySpec(sha256(properties.piiKey()), "AES");
        this.keyVersion = properties.piiKeyVersion();
    }

    @Override
    public EncryptedValue encrypt(String plaintext) {
        if (plaintext == null) {
            return new EncryptedValue(null, keyVersion);
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] envelope = ByteBuffer.allocate(iv.length + ciphertext.length)
                    .put(iv)
                    .put(ciphertext)
                    .array();
            return new EncryptedValue(Base64.getEncoder().encodeToString(envelope), keyVersion);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Sensitive data encryption failed", exception);
        }
    }

    @Override
    public String decrypt(EncryptedValue encryptedValue) {
        if (encryptedValue == null || encryptedValue.ciphertext() == null) {
            return null;
        }
        if (!keyVersion.equals(encryptedValue.keyVersion())) {
            throw new IllegalArgumentException("Unsupported encryption key version");
        }
        try {
            byte[] envelope = Base64.getDecoder().decode(encryptedValue.ciphertext());
            if (envelope.length <= IV_LENGTH) {
                throw new IllegalArgumentException("Malformed encrypted value");
            }
            byte[] iv = new byte[IV_LENGTH];
            byte[] ciphertext = new byte[envelope.length - IV_LENGTH];
            System.arraycopy(envelope, 0, iv, 0, IV_LENGTH);
            System.arraycopy(envelope, IV_LENGTH, ciphertext, 0, ciphertext.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalStateException("Sensitive data decryption failed", exception);
        }
    }

    private byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
