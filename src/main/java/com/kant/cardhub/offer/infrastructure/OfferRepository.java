package com.kant.cardhub.offer.infrastructure;

import com.kant.cardhub.offer.domain.Offer;
import com.kant.cardhub.offer.domain.OfferStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OfferRepository extends JpaRepository<Offer, UUID> {
    boolean existsByListingIdAndBuyerIdAndStatusIn(UUID listingId, UUID buyerId, Collection<OfferStatus> statuses);
    Page<Offer> findAllByBuyerId(UUID buyerId, Pageable pageable);
    Page<Offer> findAllBySellerId(UUID sellerId, Pageable pageable);
    List<Offer> findAllByListingIdAndStatusIn(UUID listingId, Collection<OfferStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Offer o where o.id = :id")
    Optional<Offer> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select o from Offer o
             where o.status in :statuses and o.expiresAt <= :now
             order by o.expiresAt asc, o.id asc
            """)
    List<Offer> findExpiredForUpdate(
            @Param("statuses") Collection<OfferStatus> statuses,
            @Param("now") Instant now,
            Pageable pageable
    );
}
