package com.kant.cardhub.order.infrastructure;

import com.kant.cardhub.offer.application.OfferService;
import com.kant.cardhub.order.application.port.AcceptedOfferPort;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class AcceptedOfferAdapter implements AcceptedOfferPort {

    private final OfferService offerService;

    public AcceptedOfferAdapter(OfferService offerService) {
        this.offerService = offerService;
    }

    @Override
    public long consume(UUID offerId, UUID buyerId, UUID listingId) {
        return offerService.consumeAcceptedOffer(offerId, buyerId, listingId);
    }
}
