package com.kant.cardhub.favorite.application;

import com.kant.cardhub.alert.application.ListingPriceReader;
import com.kant.cardhub.catalog.application.CatalogService;
import com.kant.cardhub.common.error.BusinessException;
import com.kant.cardhub.common.error.ErrorCode;
import com.kant.cardhub.favorite.domain.Favorite;
import com.kant.cardhub.favorite.infrastructure.FavoriteRepository;
import com.kant.cardhub.user.application.ActiveUserLock;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final CatalogService catalogService;

    @Autowired
    public FavoriteService(
            FavoriteRepository favoriteRepository,
            ListingPriceReader listingPriceReader,
            Clock clock,
            ActiveUserLock activeUserLock,
            CatalogService catalogService
    ) {
        this.favoriteRepository = favoriteRepository;
        this.listingPriceReader = listingPriceReader;
        this.clock = clock;
        this.activeUserLock = activeUserLock;
        this.catalogService = catalogService;
    }

    public FavoriteService(
            FavoriteRepository favoriteRepository,
            ListingPriceReader listingPriceReader,
            Clock clock,
            ActiveUserLock activeUserLock
    ) {
        this(favoriteRepository, listingPriceReader, clock, activeUserLock, null);
    }

    @Transactional
    public Favorite add(UUID userId, UUID listingId) {
        activeUserLock.requireActive(userId);
        UUID variantId = resolveLegacyListingVariant(listingId);
        return addInternal(userId, variantId, listingId);
    }

    @Transactional
    public Favorite addVariant(UUID userId, UUID variantId) {
        activeUserLock.requireActive(userId);
        if (catalogService != null && !catalogService.variantExists(variantId)) {
            throw new BusinessException(ErrorCode.CATALOG_NOT_FOUND);
        }
        return addInternal(userId, variantId, null);
    }

    private Favorite addInternal(UUID userId, UUID variantId, UUID legacyListingId) {
        if (favoriteRepository.existsByUserIdAndCardVariantId(userId, variantId)
                || (legacyListingId != null
                && favoriteRepository.existsByUserIdAndListingId(userId, legacyListingId))) {
            throw new BusinessException(ErrorCode.FAVORITE_ALREADY_EXISTS);
        }

        try {
            return favoriteRepository.saveAndFlush(
                    new Favorite(UUID.randomUUID(), userId, variantId, legacyListingId, Instant.now(clock))
            );
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.FAVORITE_ALREADY_EXISTS);
        }
    }

    @Transactional
    public void remove(UUID userId, UUID listingId) {
        UUID variantId = resolveLegacyListingVariant(listingId);
        removeVariant(userId, variantId);
    }

    @Transactional
    public void removeVariant(UUID userId, UUID variantId) {
        Favorite favorite = favoriteRepository.findByUserIdAndCardVariantId(userId, variantId)
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

    private UUID resolveLegacyListingVariant(UUID listingId) {
        return listingPriceReader.activeListingVariant(listingId).orElseGet(() -> {
            // Compatibility for older adapters/tests during the one-release transition.
            if (listingPriceReader.currentActivePrice(listingId).isPresent()) {
                return listingId;
            }
            throw new BusinessException(ErrorCode.LISTING_NOT_FOUND);
        });
    }

}
