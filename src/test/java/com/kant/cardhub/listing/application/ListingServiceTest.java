package com.kant.cardhub.listing.application;

import com.kant.cardhub.catalog.application.CatalogService;
import com.kant.cardhub.common.error.BusinessException;
import com.kant.cardhub.common.error.ErrorCode;
import com.kant.cardhub.listing.domain.CardCondition;
import com.kant.cardhub.listing.domain.Listing;
import com.kant.cardhub.listing.domain.ListingEffectiveStatus;
import com.kant.cardhub.listing.domain.ListingModerationStatus;
import com.kant.cardhub.listing.domain.ListingStatus;
import com.kant.cardhub.listing.infrastructure.ListingImageRepository;
import com.kant.cardhub.listing.infrastructure.ListingRepository;
import com.kant.cardhub.storage.application.ObjectStoragePort;
import com.kant.cardhub.storage.application.UploadService;
import com.kant.cardhub.user.application.ActiveUserLock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListingServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-25T00:00:00Z");

    @Mock private ListingRepository listingRepository;
    @Mock private ListingImageRepository imageRepository;
    @Mock private CatalogService catalogService;
    @Mock private UploadService uploadService;
    @Mock private ObjectStoragePort objectStorage;
    @Mock private ListingEventPort eventPort;
    @Mock private ActiveUserLock activeUserLock;

    private ListingService service;

    @BeforeEach
    void setUp() {
        service = new ListingService(
                listingRepository, imageRepository, catalogService, uploadService, objectStorage,
                Clock.fixed(NOW, ZoneOffset.UTC), List.of(eventPort), activeUserLock
        );
    }

    @Test
    void priceChangePublishesEventAfterValidOwnerUpdate() {
        UUID sellerId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        Listing listing = Listing.create(
                sellerId, variantId, CardCondition.NEAR_MINT, null, 10_000, 3_000, NOW.minusSeconds(1)
        );
        when(listingRepository.findById(listing.getId())).thenReturn(Optional.of(listing));
        when(imageRepository.findAllByListingIdOrderBySortOrderAsc(listing.getId())).thenReturn(List.of());

        ListingView.Detail result = service.update(
                sellerId, listing.getId(), CardCondition.MINT, "updated", 9_000, 2_000
        );

        assertThat(result.price()).isEqualTo(9_000);
        verify(eventPort).onPriceChanged(listing.getId(), variantId, sellerId, 10_000, 9_000);
    }

    @Test
    void unchangedPriceDoesNotPublishPriceEvent() {
        UUID sellerId = UUID.randomUUID();
        Listing listing = Listing.create(
                sellerId, UUID.randomUUID(), CardCondition.NEAR_MINT, null, 10_000, 3_000, NOW.minusSeconds(1)
        );
        when(listingRepository.findById(listing.getId())).thenReturn(Optional.of(listing));
        when(imageRepository.findAllByListingIdOrderBySortOrderAsc(listing.getId())).thenReturn(List.of());

        service.update(sellerId, listing.getId(), CardCondition.MINT, null, 10_000, 0);

        verifyNoInteractions(eventPort);
    }

    @Test
    void publicActiveListingProjectsActiveEffectiveStatus() {
        Listing listing = Listing.create(
                UUID.randomUUID(), UUID.randomUUID(), CardCondition.NEAR_MINT,
                null, 10_000, 3_000, NOW
        );
        when(listingRepository.findById(listing.getId())).thenReturn(Optional.of(listing));
        when(imageRepository.findAllByListingIdOrderBySortOrderAsc(listing.getId())).thenReturn(List.of());

        ListingView.Detail result = service.detail(listing.getId());

        assertThat(result.status()).isEqualTo(ListingStatus.ACTIVE);
        assertThat(result.moderationStatus()).isEqualTo(ListingModerationStatus.CLEAR);
        assertThat(result.effectiveStatus()).isEqualTo(ListingEffectiveStatus.ACTIVE);
    }

    @Test
    void blockedListingRemainsHiddenFromPublicDetail() {
        Listing listing = Listing.create(
                UUID.randomUUID(), UUID.randomUUID(), CardCondition.NEAR_MINT,
                null, 10_000, 3_000, NOW.minusSeconds(1)
        );
        listing.block(UUID.randomUUID(), "관리자 차단", NOW);
        when(listingRepository.findById(listing.getId())).thenReturn(Optional.of(listing));

        assertThatThrownBy(() -> service.detail(listing.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(ErrorCode.LISTING_NOT_FOUND);
        verifyNoInteractions(imageRepository);
    }

    @Test
    void mineKeepsRawStatusAndProjectsBlockedEffectiveStatus() {
        UUID sellerId = UUID.randomUUID();
        Listing listing = Listing.create(
                sellerId, UUID.randomUUID(), CardCondition.NEAR_MINT,
                null, 10_000, 3_000, NOW.minusSeconds(1)
        );
        listing.block(UUID.randomUUID(), "관리자 차단", NOW);
        when(listingRepository.findAllBySellerIdAndStatusNot(
                org.mockito.ArgumentMatchers.eq(sellerId),
                org.mockito.ArgumentMatchers.eq(ListingStatus.DELETED),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(listing)));
        when(imageRepository.findAllByListingIdInOrderByListingIdAscSortOrderAsc(List.of(listing.getId())))
                .thenReturn(List.of());

        ListingView.Summary result = service.mine(sellerId, 0, 20).items().getFirst();

        assertThat(result.status()).isEqualTo(ListingStatus.HIDDEN);
        assertThat(result.moderationStatus()).isEqualTo(ListingModerationStatus.BLOCKED);
        assertThat(result.effectiveStatus()).isEqualTo(ListingEffectiveStatus.BLOCKED);
    }
}
