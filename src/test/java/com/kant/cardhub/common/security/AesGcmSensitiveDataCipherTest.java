package com.kant.cardhub.common.security;

import com.kant.cardhub.common.config.SecurityDataProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AesGcmSensitiveDataCipherTest {

    @Test
    void encryptsWithRandomIvAndDecryptsTheEnvelope() {
        AesGcmSensitiveDataCipher cipher = new AesGcmSensitiveDataCipher(
                new SecurityDataProperties("aes", "test-key", "test-v1", "", "ap-northeast-2")
        );

        SensitiveDataCipher.EncryptedValue first = cipher.encrypt("서울시 테스트 주소");
        SensitiveDataCipher.EncryptedValue second = cipher.encrypt("서울시 테스트 주소");

        assertThat(first.ciphertext()).isNotEqualTo(second.ciphertext());
        assertThat(first.keyVersion()).isEqualTo("test-v1");
        assertThat(cipher.decrypt(first)).isEqualTo("서울시 테스트 주소");
    }
}
