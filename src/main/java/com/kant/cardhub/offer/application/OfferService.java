package com.kant.cardhub.offer.application;

import com.kant.cardhub.common.error.BusinessException;
import com.kant.cardhub.common.error.ErrorCode;
import com.kant.cardhub.listing.domain.Listing;
import com.kant.cardhub.listing.domain.ListingStatus;
import com.kant.cardhub.listing.infrastructure.ListingRepository;
import com.kant.cardhub.offer.domain.Offer;
import com.kant.cardhub.offer.domain.OfferAction;
import com.kant.cardhub.offer.domain.OfferActionType;
import com.kant.cardhub.offer.domain.OfferDecision;
import com.kant.cardhub.offer.domain.OfferStatus;
import com.kant.cardhub.offer.infrastructure.OfferActionRepository;
import com.kant.cardhub.offer.infrastructure.OfferRepository;
import com.kant.cardhub.user.application.ActiveUserLock;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class OfferService {

    private static final Duration OFFER_TTL = Duration.ofHours(48);
    private static final EnumSet<OfferStatus> ACTIVE_FOR_BUYER = EnumSet.of(
            OfferStatus.PENDING, OfferStatus.COUNTERED, OfferStatus.ACCEPTED
    );
    private static final EnumSet<OfferStatus> EXPIRABLE = EnumSet.of(
            OfferStatus.PENDING, OfferStatus.COUNTERED, OfferStatus.ACCEPTED
    );

    private final OfferRepository offerRepository;
    private final OfferActionRepository actionRepository;
    private final ListingRepository listingRepository;
    private final Clock clock;
    private final List<OfferEventPort> eventPorts;
    private final ActiveUserLock activeUserLock;

    public OfferService(
            OfferRepository offerRepository,
            OfferActionRepository actionRepository,
            ListingRepository listingRepository,
            Clock clock,
            List<OfferEventPort> eventPorts,
            ActiveUserLock activeUserLock
    ) {
        this.offerRepository = offerRepository;
        this.actionRepository = actionRepository;
        this.listingRepository = listingRepository;
        this.clock = clock;
        this.eventPorts = List.copyOf(eventPorts);
        this.activeUserLock = activeUserLock;
    }

    @Transactional
    public OfferView.Detail create(UUID buyerId, UUID listingId, long offeredPrice) {
        activeUserLock.requireActive(buyerId);
        Listing listing = listingRepository.findByIdForUpdate(listingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.LISTING_NOT_FOUND));
        if (listing.getStatus() != ListingStatus.ACTIVE) throw new BusinessException(ErrorCode.LISTING_INVALID_STATE);
        if (listing.isOwnedBy(buyerId)) throw new BusinessException(ErrorCode.OFFER_FORBIDDEN);
        if (offeredPrice < Offer.MIN_PRICE || offeredPrice > listing.getPrice()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "제안 가격은 500원 이상, 매물 가격 이하여야 합니다.");
        }
        if (offerRepository.existsByListingIdAndBuyerIdAndStatusIn(listingId, buyerId, ACTIVE_FOR_BUYER)) {
            throw new BusinessException(ErrorCode.CONFLICT, "이 매물에 진행 중인 제안이 있습니다.");
        }
        Instant now = clock.instant();
        try {
            Offer offer = offerRepository.saveAndFlush(Offer.create(
                    listingId, buyerId, listing.getSellerId(), offeredPrice, now.plus(OFFER_TTL), now
            ));
            actionRepository.save(OfferAction.create(
                    offer.getId(), buyerId, OfferActionType.CREATED, offeredPrice, now
            ));
            return toDetail(offer);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.CONFLICT, "이 매물에 진행 중인 제안이 있습니다.");
        }
    }

    @Transactional
    public OfferView.Detail respond(
            UUID actorId,
            UUID offerId,
            OfferDecision decision,
            Long counterPrice
    ) {
        activeUserLock.requireActive(actorId);
        Listing lockedListing = null;
        Offer offer;
        if (decision == OfferDecision.ACCEPT || decision == OfferDecision.COUNTER) {
            Offer preview = offerRepository.findById(offerId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.OFFER_NOT_FOUND));
            lockedListing = lockActiveListing(preview.getListingId());
            offer = locked(offerId);
        } else {
            offer = locked(offerId);
        }
        Instant now = clock.instant();
        OfferActionType actionType;
        Long actionPrice;

        switch (decision) {
            case ACCEPT -> {
                offer.accept(actorId, now);
                actionType = OfferActionType.ACCEPTED;
                actionPrice = offer.acceptedPrice();
                rejectCompetingOffers(offer, now);
            }
            case REJECT -> {
                offer.reject(actorId, now);
                actionType = OfferActionType.REJECTED;
                actionPrice = null;
            }
            case COUNTER -> {
                if (counterPrice == null) {
                    throw new BusinessException(ErrorCode.VALIDATION_ERROR, "역제안 가격이 필요합니다.");
                }
                offer.counter(actorId, counterPrice, lockedListing.getPrice(), now);
                actionType = OfferActionType.COUNTERED;
                actionPrice = counterPrice;
            }
            default -> throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }

        actionRepository.save(OfferAction.create(offer.getId(), actorId, actionType, actionPrice, now));
        UUID recipientId = actorId.equals(offer.getBuyerId()) ? offer.getSellerId() : offer.getBuyerId();
        eventPorts.forEach(port -> port.onReply(
                recipientId,
                offer.getId(),
                offer.getStatus(),
                "offer-reply:" + offer.getId() + ":" + offer.getVersion() + ":" + offer.getStatus()
        ));
        try {
            offerRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.CONFLICT, "이미 수락된 다른 제안이 있습니다.");
        }
        return toDetail(offer);
    }

    public OfferView.WithHistory detail(UUID actorId, UUID offerId) {
        Offer offer = offerRepository.findById(offerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.OFFER_NOT_FOUND));
        requireParticipant(offer, actorId);
        return new OfferView.WithHistory(
                toDetail(offer),
                actionRepository.findAllByOfferIdOrderByCreatedAtAsc(offerId).stream()
                        .map(action -> new OfferView.Action(
                                action.getActorId(), action.getActionType(), action.getPrice(), action.getCreatedAt()
                        )).toList()
        );
    }

    public OfferView.Page<OfferView.Detail> mine(UUID userId, OfferRole role, int page, int size) {
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        var result = role == OfferRole.SELLER
                ? offerRepository.findAllBySellerId(userId, pageable)
                : offerRepository.findAllByBuyerId(userId, pageable);
        return new OfferView.Page<>(
                result.getContent().stream().map(OfferService::toDetail).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages()
        );
    }

    @Transactional
    public long consumeAcceptedOffer(UUID offerId, UUID buyerId, UUID listingId) {
        Offer offer = locked(offerId);
        long price = offer.consume(buyerId, listingId, clock.instant());
        actionRepository.save(OfferAction.create(
                offer.getId(), buyerId, OfferActionType.CONSUMED, price, clock.instant()
        ));
        return price;
    }

    @Transactional
    public int expireDueOffers(int requestedLimit) {
        int limit = Math.max(1, Math.min(requestedLimit, 500));
        Instant now = clock.instant();
        List<Offer> offers = offerRepository.findExpiredForUpdate(
                EXPIRABLE, now, PageRequest.of(0, limit)
        );
        int expired = 0;
        for (Offer offer : offers) {
            if (offer.expire(now)) {
                expired++;
                actionRepository.save(OfferAction.create(
                        offer.getId(), null, OfferActionType.EXPIRED, null, now
                ));
                eventPorts.forEach(port -> port.onExpired(
                        offer.getBuyerId(), offer.getId(), "offer-expired:" + offer.getId()
                ));
            }
        }
        return expired;
    }

    private Offer locked(UUID offerId) {
        return offerRepository.findByIdForUpdate(offerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.OFFER_NOT_FOUND));
    }

    private Listing lockActiveListing(UUID listingId) {
        Listing listing = listingRepository.findByIdForUpdate(listingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.LISTING_NOT_FOUND));
        if (listing.getStatus() != ListingStatus.ACTIVE) throw new BusinessException(ErrorCode.LISTING_INVALID_STATE);
        return listing;
    }

    private void rejectCompetingOffers(Offer accepted, Instant now) {
        offerRepository.findAllByListingIdAndStatusIn(
                accepted.getListingId(), EnumSet.of(OfferStatus.PENDING, OfferStatus.COUNTERED)
        ).stream().filter(other -> !other.getId().equals(accepted.getId())).forEach(other -> {
            other.rejectAfterCompetingOfferAccepted(now);
            actionRepository.save(OfferAction.create(
                    other.getId(), null, OfferActionType.REJECTED, null, now
            ));
            eventPorts.forEach(port -> port.onReply(
                    other.getBuyerId(), other.getId(), other.getStatus(),
                    "offer-reply:" + other.getId() + ":competing-rejected"
            ));
        });
    }

    private static void requireParticipant(Offer offer, UUID actorId) {
        if (!offer.getBuyerId().equals(actorId) && !offer.getSellerId().equals(actorId)) {
            throw new BusinessException(ErrorCode.OFFER_FORBIDDEN);
        }
    }

    private static OfferView.Detail toDetail(Offer offer) {
        Long acceptedPrice = offer.getStatus() == OfferStatus.ACCEPTED || offer.getStatus() == OfferStatus.CONSUMED
                ? offer.acceptedPrice() : null;
        return new OfferView.Detail(
                offer.getId(), offer.getListingId(), offer.getBuyerId(), offer.getSellerId(),
                offer.getOfferedPrice(), offer.getCounterPrice(), acceptedPrice, offer.getStatus(),
                offer.getExpiresAt(), offer.getCreatedAt(), offer.getUpdatedAt()
        );
    }
}
