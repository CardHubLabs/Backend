package com.kant.cardhub.order.application;

import com.kant.cardhub.order.domain.ShippingAddressSnapshot;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class ShippingAddressCodecTest {

    private final ShippingAddressCodec codec = new ShippingAddressCodec(JsonMapper.builder().build());

    @Test
    void versionTwoAddressRoundTripsAsStructuredJson() {
        ShippingAddressSnapshot source = new ShippingAddressSnapshot(
                "홍길동", "010-1234-5678", "06236", "서울특별시 강남구 테헤란로 1", "101호");

        String json = codec.encode(source);

        assertThat(json).contains("recipientName", "postalCode");
        assertThat(codec.decode(json)).isEqualTo(source);
    }
}
