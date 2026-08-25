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
                        """)
                .setParameter("listingId", listingId)
                .getResultStream()
                .findFirst()
                .orElse(null);
        return value == null ? OptionalLong.empty() : OptionalLong.of(((Number) value).longValue());
    }
}
