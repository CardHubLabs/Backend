package com.kant.cardhub.listing.application;

import com.kant.cardhub.catalog.application.CatalogService;
import com.kant.cardhub.listing.domain.CardCondition;
import com.kant.cardhub.listing.domain.Listing;
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

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
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
}
