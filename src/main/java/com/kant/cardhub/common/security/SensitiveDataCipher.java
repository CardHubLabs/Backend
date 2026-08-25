package com.kant.cardhub.common.security;

public interface SensitiveDataCipher {

    EncryptedValue encrypt(String plaintext);

    String decrypt(EncryptedValue encryptedValue);

    record EncryptedValue(String ciphertext, String keyVersion) {
    }
}
