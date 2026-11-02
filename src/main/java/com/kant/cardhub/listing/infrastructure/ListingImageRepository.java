package com.kant.cardhub.listing.infrastructure;

import com.kant.cardhub.listing.domain.ListingImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ListingImageRepository extends JpaRepository<ListingImage, UUID> {
    List<ListingImage> findAllByListingIdOrderBySortOrderAsc(UUID listingId);
    List<ListingImage> findAllByListingIdInOrderByListingIdAscSortOrderAsc(List<UUID> listingIds);
}
