package com.kant.cardhub.offer.infrastructure;

import com.kant.cardhub.offer.domain.OfferAction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OfferActionRepository extends JpaRepository<OfferAction, UUID> {
    List<OfferAction> findAllByOfferIdOrderByCreatedAtAsc(UUID offerId);
}
