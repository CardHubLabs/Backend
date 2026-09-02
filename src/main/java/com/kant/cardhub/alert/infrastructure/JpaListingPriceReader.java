package com.kant.cardhub.alert.infrastructure;

import com.kant.cardhub.alert.application.ListingPriceReader;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

import java.util.OptionalLong;
import java.util.UUID;

@Repository
public class JpaListingPriceReader implements ListingPriceReader {

    private final EntityManager entityManager;

    public JpaListingPriceReader(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public OptionalLong currentActivePrice(UUID listingId) {
        Object value = entityManager.createNativeQuery("""
                        SELECT price
                          FROM listings
                         WHERE id = :listingId
                           AND status = 'ACTIVE'
                           AND moderation_status = 'CLEAR'
                        """)
                .setParameter("listingId", listingId)
                .getResultStream()
                .findFirst()
                .orElse(null);
        return value == null ? OptionalLong.empty() : OptionalLong.of(((Number) value).longValue());
    }

    @Override
    public OptionalLong currentActiveLowestPrice(UUID cardVariantId) {
        Object value = entityManager.createNativeQuery("""
                        SELECT MIN(price)
                          FROM listings
                         WHERE card_variant_id = :cardVariantId
                           AND status = 'ACTIVE'
                           AND moderation_status = 'CLEAR'
                        """)
                .setParameter("cardVariantId", cardVariantId)
                .getSingleResult();
        return value == null ? OptionalLong.empty() : OptionalLong.of(((Number) value).longValue());
    }

    @Override
    public java.util.Optional<UUID> activeListingVariant(UUID listingId) {
        Object value = entityManager.createNativeQuery("""
                        SELECT card_variant_id
                          FROM listings
                         WHERE id = :listingId
                           AND status = 'ACTIVE'
                           AND moderation_status = 'CLEAR'
                        """)
                .setParameter("listingId", listingId)
                .getResultStream()
                .findFirst()
                .orElse(null);
        if (value == null) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(value instanceof UUID uuid
                ? uuid : UUID.fromString(String.valueOf(value)));
    }
}
