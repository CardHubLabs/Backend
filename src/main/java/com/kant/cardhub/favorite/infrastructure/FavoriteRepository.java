package com.kant.cardhub.favorite.infrastructure;

import com.kant.cardhub.favorite.domain.Favorite;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface FavoriteRepository extends JpaRepository<Favorite, UUID> {

    boolean existsByUserIdAndListingId(UUID userId, UUID listingId);

    boolean existsByUserIdAndCardVariantId(UUID userId, UUID cardVariantId);

    Optional<Favorite> findByUserIdAndListingId(UUID userId, UUID listingId);

    Optional<Favorite> findByUserIdAndCardVariantId(UUID userId, UUID cardVariantId);

    Page<Favorite> findAllByUserId(UUID userId, Pageable pageable);
}
