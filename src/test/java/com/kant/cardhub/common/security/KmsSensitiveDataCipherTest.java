package com.kant.cardhub.common.security;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.DataKeySpec;
import software.amazon.awssdk.services.kms.model.DecryptRequest;
import software.amazon.awssdk.services.kms.model.DecryptResponse;
import software.amazon.awssdk.services.kms.model.GenerateDataKeyRequest;
import software.amazon.awssdk.services.kms.model.GenerateDataKeyResponse;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KmsSensitiveDataCipherTest {

    @Test
    void encryptsWithFreshDataKeysAndDecryptsThePersistedEnvelope() {
        KmsClient kmsClient = mock(KmsClient.class);
        byte[] plaintextDataKey = new byte[32];
        Arrays.fill(plaintextDataKey, (byte) 7);
        byte[] encryptedDataKey = "encrypted-data-key".getBytes(StandardCharsets.UTF_8);

        when(kmsClient.generateDataKey(any(GenerateDataKeyRequest.class)))
                .thenAnswer(invocation -> GenerateDataKeyResponse.builder()
                        .plaintext(SdkBytes.fromByteArray(plaintextDataKey))
                        .ciphertextBlob(SdkBytes.fromByteArray(encryptedDataKey))
                        .build());
        when(kmsClient.decrypt(any(DecryptRequest.class)))
                .thenReturn(DecryptResponse.builder()
                        .plaintext(SdkBytes.fromByteArray(plaintextDataKey))
                        .build());

        KmsSensitiveDataCipher cipher = new KmsSensitiveDataCipher(kmsClient, "alias/cardhub-pii");
        SensitiveDataCipher.EncryptedValue first = cipher.encrypt("서울시 테스트 주소");
        SensitiveDataCipher.EncryptedValue second = cipher.encrypt("서울시 테스트 주소");

        assertThat(first.ciphertext()).isNotEqualTo(second.ciphertext());
        assertThat(first.keyVersion()).startsWith("kms-v1:");
        assertThat(cipher.decrypt(first)).isEqualTo("서울시 테스트 주소");

        ArgumentCaptor<GenerateDataKeyRequest> generateRequest =
                ArgumentCaptor.forClass(GenerateDataKeyRequest.class);
        verify(kmsClient, org.mockito.Mockito.atLeastOnce()).generateDataKey(generateRequest.capture());
        assertThat(generateRequest.getValue().keySpec()).isEqualTo(DataKeySpec.AES_256);
        assertThat(generateRequest.getValue().keyId()).isEqualTo("alias/cardhub-pii");

        ArgumentCaptor<DecryptRequest> decryptRequest = ArgumentCaptor.forClass(DecryptRequest.class);
        verify(kmsClient).decrypt(decryptRequest.capture());
        assertThat(decryptRequest.getValue().ciphertextBlob().asByteArray())
                .containsExactly(encryptedDataKey);
        assertThat(decryptRequest.getValue().encryptionContext())
                .containsEntry("purpose", "pii")
                .containsEntry("envelopeVersion", first.keyVersion());
    }
}
