package com.kant.cardhub;

import com.kant.cardhub.common.security.KmsSensitiveDataCipher;
import com.kant.cardhub.common.security.SensitiveDataCipher;
import com.kant.cardhub.payment.application.port.PaymentGateway;
import com.kant.cardhub.payment.infrastructure.TossPaymentGateway;
import com.kant.cardhub.shipment.application.port.ShipmentTrackingPort;
import com.kant.cardhub.shipment.infrastructure.SweetTrackerShipmentAdapter;
import com.kant.cardhub.storage.application.ObjectStoragePort;
import com.kant.cardhub.storage.infrastructure.S3ObjectStorageAdapter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:cardhub-prod-adapters;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.flyway.enabled=false",
                "cardhub.auth.jwt-secret=cardhub-prod-adapter-test-jwt-secret-at-least-32-bytes",
                "cardhub.auth.oauth-success-url=https://cardhub.example/auth/callback",
                "cardhub.cors.allowed-origins=https://cardhub.example",
                "cardhub.security.cipher-provider=kms",
                "cardhub.security.kms-key-id=alias/cardhub-test",
                "cardhub.security.kms-region=ap-northeast-2",
                "cardhub.storage.provider=s3",
                "cardhub.storage.bucket=cardhub-test",
                "cardhub.storage.region=ap-northeast-2",
                "cardhub.storage.cloudfront-base-url=https://cdn.cardhub.example",
                "cardhub.payment.provider=toss",
                "cardhub.payment.secret-key=test_toss_secret",
                "cardhub.shipment.provider=sweettracker",
                "cardhub.shipment.api-key=test_sweettracker_key",
                "cardhub.worker.enabled=false"
        }
)
@ActiveProfiles({"test", "prod"})
class ProductionAdapterContextTest {

    @Autowired PaymentGateway paymentGateway;
    @Autowired ShipmentTrackingPort shipmentTrackingPort;
    @Autowired ObjectStoragePort objectStoragePort;
    @Autowired SensitiveDataCipher sensitiveDataCipher;

    @Test
    void productionAdaptersAndKmsCipherCanBootstrapTogether() {
        assertThat(paymentGateway).isInstanceOf(TossPaymentGateway.class);
        assertThat(shipmentTrackingPort).isInstanceOf(SweetTrackerShipmentAdapter.class);
        assertThat(objectStoragePort).isInstanceOf(S3ObjectStorageAdapter.class);
        assertThat(sensitiveDataCipher).isInstanceOf(KmsSensitiveDataCipher.class);
    }
}
