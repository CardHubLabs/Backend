package com.kant.cardhub.favorite.application;

import com.kant.cardhub.alert.application.ListingPriceReader;
import com.kant.cardhub.common.error.BusinessException;
import com.kant.cardhub.common.error.ErrorCode;
import com.kant.cardhub.favorite.domain.Favorite;
import com.kant.cardhub.favorite.infrastructure.FavoriteRepository;
import com.kant.cardhub.user.application.ActiveUserLock;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class FavoriteService {

    private final FavoriteRepository favoriteRepository;
    private final ListingPriceReader listingPriceReader;
    private final Clock clock;
    private final ActiveUserLock activeUserLock;

    public FavoriteService(
            FavoriteRepository favoriteRepository,
            ListingPriceReader listingPriceReader,
            Clock clock,
            ActiveUserLock activeUserLock
    ) {
        this.favoriteRepository = favoriteRepository;
        this.listingPriceReader = listingPriceReader;
        this.clock = clock;
        this.activeUserLock = activeUserLock;
    }

    @Transactional
    public Favorite add(UUID userId, UUID listingId) {
        activeUserLock.requireActive(userId);
        requireActiveListing(listingId);
        if (favoriteRepository.existsByUserIdAndListingId(userId, listingId)) {
            throw new BusinessException(ErrorCode.FAVORITE_ALREADY_EXISTS);
        }

        try {
            return favoriteRepository.saveAndFlush(
                    new Favorite(UUID.randomUUID(), userId, listingId, Instant.now(clock))
            );
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.FAVORITE_ALREADY_EXISTS);
        }
    }

    @Transactional
    public void remove(UUID userId, UUID listingId) {
        Favorite favorite = favoriteRepository.findByUserIdAndListingId(userId, listingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FAVORITE_NOT_FOUND));
        favoriteRepository.delete(favorite);
    }

    @Transactional(readOnly = true)
    public Page<Favorite> list(UUID userId, int page, int size) {
        PageRequest pageable = PageRequest.of(
                Math.max(page, 0),
                Math.clamp(size, 1, 50),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))
        );
        return favoriteRepository.findAllByUserId(userId, pageable);
    }

    private void requireActiveListing(UUID listingId) {
        if (listingPriceReader.currentActivePrice(listingId).isEmpty()) {
            throw new BusinessException(ErrorCode.LISTING_NOT_FOUND);
        }
    }
}
