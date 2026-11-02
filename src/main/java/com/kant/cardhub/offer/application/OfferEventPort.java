package com.kant.cardhub.offer.application;

import com.kant.cardhub.offer.domain.OfferStatus;

import java.util.UUID;

public interface OfferEventPort {
    void onReply(UUID recipientId, UUID offerId, OfferStatus status, String dedupeKey);

    void onExpired(UUID recipientId, UUID offerId, String dedupeKey);
}
