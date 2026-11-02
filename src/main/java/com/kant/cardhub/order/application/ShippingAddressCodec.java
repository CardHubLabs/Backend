package com.kant.cardhub.order.application;

import com.kant.cardhub.order.domain.ShippingAddressSnapshot;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
public class ShippingAddressCodec {

    private final ObjectMapper objectMapper;

    public ShippingAddressCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String encode(ShippingAddressSnapshot address) {
        try {
            return objectMapper.writeValueAsString(address);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Shipping address could not be serialized", exception);
        }
    }

    public ShippingAddressSnapshot decode(String value) {
        try {
            return objectMapper.readValue(value, ShippingAddressSnapshot.class);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Stored shipping address is invalid", exception);
        }
    }
}
