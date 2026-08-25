package com.kant.cardhub.common.security;

import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.DataKeySpec;
import software.amazon.awssdk.services.kms.model.DecryptRequest;
import software.amazon.awssdk.services.kms.model.DecryptResponse;
import software.amazon.awssdk.services.kms.model.GenerateDataKeyRequest;
import software.amazon.awssdk.services.kms.model.GenerateDataKeyResponse;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;

/**
 * Envelope encryption for sensitive application data. KMS protects a fresh AES-256
 * data key for each value; only the encrypted data key is persisted with the value.
 */
public final class KmsSensitiveDataCipher implements SensitiveDataCipher {

    private static final byte[] MAGIC = {'C', 'H', 'K', '1'};
    private static final String KEY_VERSION_PREFIX = "kms-v1:";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final int AES_256_KEY_LENGTH = 32;
    private static final int MAX_ENCRYPTED_DATA_KEY_LENGTH = 64 * 1024;
    private static final int MAX_ENVELOPE_LENGTH = 128 * 1024;

    private final KmsClient kmsClient;
    private final String kmsKeyId;
    private final String keyVersion;
    private final SecureRandom secureRandom;

    public KmsSensitiveDataCipher(KmsClient kmsClient, String kmsKeyId) {
        this(kmsClient, kmsKeyId, new SecureRandom());
    }

    KmsSensitiveDataCipher(KmsClient kmsClient, String kmsKeyId, SecureRandom secureRandom) {
        if (kmsKeyId == null || kmsKeyId.isBlank()) {
            throw new IllegalArgumentException("KMS key ID must not be blank");
        }
        this.kmsClient = kmsClient;
        this.kmsKeyId = kmsKeyId;
        this.keyVersion = KEY_VERSION_PREFIX + keyFingerprint(kmsKeyId);
        this.secureRandom = secureRandom;
    }

    @Override
    public EncryptedValue encrypt(String plaintext) {
        if (plaintext == null) {
            return new EncryptedValue(null, keyVersion);
        }

        GenerateDataKeyResponse generated = kmsClient.generateDataKey(GenerateDataKeyRequest.builder()
                .keyId(kmsKeyId)
                .keySpec(DataKeySpec.AES_256)
                .encryptionContext(encryptionContext(keyVersion))
                .build());
        byte[] dataKey = sensitiveBytes(generated.plaintext(), "KMS did not return a plaintext data key");
        byte[] encryptedDataKey = bytes(generated.ciphertextBlob(), "KMS did not return an encrypted data key");

        try {
            if (dataKey.length != AES_256_KEY_LENGTH) {
                throw new IllegalStateException("KMS returned an invalid AES-256 data key");
            }
            if (encryptedDataKey.length > MAX_ENCRYPTED_DATA_KEY_LENGTH) {
                throw new IllegalStateException("KMS encrypted data key is too large");
            }

            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);
            byte[] ciphertext = aesGcm(Cipher.ENCRYPT_MODE, dataKey, iv,
                    plaintext.getBytes(StandardCharsets.UTF_8), keyVersion);
            int envelopeLength = MAGIC.length + Integer.BYTES + encryptedDataKey.length + iv.length + ciphertext.length;
            if (envelopeLength > MAX_ENVELOPE_LENGTH) {
                throw new IllegalStateException("Sensitive data envelope is too large");
            }

            byte[] envelope = ByteBuffer.allocate(envelopeLength)
                    .put(MAGIC)
                    .putInt(encryptedDataKey.length)
                    .put(encryptedDataKey)
                    .put(iv)
                    .put(ciphertext)
                    .array();
            return new EncryptedValue(Base64.getEncoder().encodeToString(envelope), keyVersion);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Sensitive data encryption failed", exception);
        } finally {
            Arrays.fill(dataKey, (byte) 0);
        }
    }

    @Override
    public String decrypt(EncryptedValue encryptedValue) {
        if (encryptedValue == null || encryptedValue.ciphertext() == null) {
            return null;
        }
        String storedKeyVersion = encryptedValue.keyVersion();
        if (storedKeyVersion == null || !storedKeyVersion.startsWith(KEY_VERSION_PREFIX)) {
            throw new IllegalArgumentException("Unsupported encryption key version");
        }

        Envelope envelope = decodeEnvelope(encryptedValue.ciphertext());
        DecryptResponse decrypted = kmsClient.decrypt(DecryptRequest.builder()
                .ciphertextBlob(SdkBytes.fromByteArray(envelope.encryptedDataKey()))
                .encryptionContext(encryptionContext(storedKeyVersion))
                .build());
        byte[] dataKey = sensitiveBytes(decrypted.plaintext(), "KMS did not return a plaintext data key");

        try {
            if (dataKey.length != AES_256_KEY_LENGTH) {
                throw new IllegalStateException("KMS returned an invalid AES-256 data key");
            }
            byte[] plaintext = aesGcm(Cipher.DECRYPT_MODE, dataKey, envelope.iv(),
                    envelope.ciphertext(), storedKeyVersion);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Sensitive data decryption failed", exception);
        } finally {
            Arrays.fill(dataKey, (byte) 0);
        }
    }

    private Envelope decodeEnvelope(String encoded) {
        if (encoded.length() > MAX_ENVELOPE_LENGTH * 2) {
            throw new IllegalStateException("Malformed encrypted value");
        }
        try {
            byte[] envelope = Base64.getDecoder().decode(encoded);
            int minimumLength = MAGIC.length + Integer.BYTES + 1 + IV_LENGTH + (TAG_LENGTH_BITS / Byte.SIZE);
            if (envelope.length < minimumLength || envelope.length > MAX_ENVELOPE_LENGTH) {
                throw new IllegalArgumentException("Invalid envelope length");
            }

            ByteBuffer buffer = ByteBuffer.wrap(envelope);
            byte[] magic = new byte[MAGIC.length];
            buffer.get(magic);
            if (!Arrays.equals(MAGIC, magic)) {
                throw new IllegalArgumentException("Invalid envelope version");
            }
            int encryptedKeyLength = buffer.getInt();
            int minimumRemainder = IV_LENGTH + (TAG_LENGTH_BITS / Byte.SIZE);
            if (encryptedKeyLength <= 0
                    || encryptedKeyLength > MAX_ENCRYPTED_DATA_KEY_LENGTH
                    || encryptedKeyLength > buffer.remaining() - minimumRemainder) {
                throw new IllegalArgumentException("Invalid encrypted data key length");
            }

            byte[] encryptedDataKey = new byte[encryptedKeyLength];
            byte[] iv = new byte[IV_LENGTH];
            byte[] ciphertext = new byte[buffer.remaining() - encryptedKeyLength - IV_LENGTH];
            buffer.get(encryptedDataKey);
            buffer.get(iv);
            buffer.get(ciphertext);
            return new Envelope(encryptedDataKey, iv, ciphertext);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Malformed encrypted value", exception);
        }
    }

    private byte[] aesGcm(int mode, byte[] dataKey, byte[] iv, byte[] input, String storedKeyVersion)
            throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, new SecretKeySpec(dataKey, "AES"), new GCMParameterSpec(TAG_LENGTH_BITS, iv));
        cipher.updateAAD(("cardhub:pii:" + storedKeyVersion).getBytes(StandardCharsets.UTF_8));
        return cipher.doFinal(input);
    }

    private Map<String, String> encryptionContext(String storedKeyVersion) {
        return Map.of(
                "application", "cardhub",
                "purpose", "pii",
                "envelopeVersion", storedKeyVersion
        );
    }

    private byte[] bytes(SdkBytes value, String message) {
        if (value == null) {
            throw new IllegalStateException(message);
        }
        byte[] bytes = value.asByteArray();
        if (bytes.length == 0) {
            throw new IllegalStateException(message);
        }
        return bytes;
    }

    private byte[] sensitiveBytes(SdkBytes value, String message) {
        if (value == null) {
            throw new IllegalStateException(message);
        }
        // KMS response bytes are otherwise retained by the immutable response object.
        // Taking the backing array lets the finally block erase that copy as well.
        byte[] bytes = value.asByteArrayUnsafe();
        if (bytes.length == 0) {
            throw new IllegalStateException(message);
        }
        return bytes;
    }

    private static String keyFingerprint(String keyId) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(keyId.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(Arrays.copyOf(digest, 12));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private record Envelope(byte[] encryptedDataKey, byte[] iv, byte[] ciphertext) {
    }
}
