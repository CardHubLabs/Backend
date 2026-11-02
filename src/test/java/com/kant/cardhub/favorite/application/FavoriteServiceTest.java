package com.kant.cardhub.favorite.application;

import com.kant.cardhub.alert.application.ListingPriceReader;
import com.kant.cardhub.common.error.BusinessException;
import com.kant.cardhub.common.error.ErrorCode;
import com.kant.cardhub.favorite.domain.Favorite;
import com.kant.cardhub.favorite.infrastructure.FavoriteRepository;
import com.kant.cardhub.user.application.ActiveUserLock;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.OptionalLong;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FavoriteServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-25T00:00:00Z");

    @Test
    void addsActiveListingToFavorites() {
        FavoriteRepository repository = mock(FavoriteRepository.class);
        ListingPriceReader listingReader = mock(ListingPriceReader.class);
        ActiveUserLock activeUserLock = mock(ActiveUserLock.class);
        FavoriteService service = new FavoriteService(
                repository, listingReader, Clock.fixed(NOW, ZoneOffset.UTC), activeUserLock
        );
        UUID userId = UUID.randomUUID();
        UUID listingId = UUID.randomUUID();
        when(listingReader.currentActivePrice(listingId)).thenReturn(OptionalLong.of(10_000));
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Favorite favorite = service.add(userId, listingId);

        assertThat(favorite.getUserId()).isEqualTo(userId);
        assertThat(favorite.getListingId()).isEqualTo(listingId);
        assertThat(favorite.getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void rejectsDuplicateFavorite() {
        FavoriteRepository repository = mock(FavoriteRepository.class);
        ListingPriceReader listingReader = mock(ListingPriceReader.class);
        ActiveUserLock activeUserLock = mock(ActiveUserLock.class);
        FavoriteService service = new FavoriteService(
                repository, listingReader, Clock.fixed(NOW, ZoneOffset.UTC), activeUserLock
        );
        UUID userId = UUID.randomUUID();
        UUID listingId = UUID.randomUUID();
        when(listingReader.currentActivePrice(listingId)).thenReturn(OptionalLong.of(10_000));
        when(repository.existsByUserIdAndListingId(userId, listingId)).thenReturn(true);

        assertThatThrownBy(() -> service.add(userId, listingId))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode())
                                .isEqualTo(ErrorCode.FAVORITE_ALREADY_EXISTS));
    }
}
