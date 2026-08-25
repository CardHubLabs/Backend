package com.kant.cardhub.listing.infrastructure;

import com.kant.cardhub.listing.domain.Listing;
import com.kant.cardhub.listing.domain.ListingStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ListingRepository extends JpaRepository<Listing, UUID> {

    @Query("""
            select l from Listing l
            where l.status = :status
              and (:cardVariantId is null or l.cardVariantId = :cardVariantId)
              and (:minPrice is null or l.price >= :minPrice)
              and (:maxPrice is null or l.price <= :maxPrice)
            """)
    Page<Listing> search(
            @Param("status") ListingStatus status,
            @Param("cardVariantId") UUID cardVariantId,
            @Param("minPrice") Long minPrice,
            @Param("maxPrice") Long maxPrice,
            Pageable pageable
    );

    Page<Listing> findAllBySellerIdAndStatusNot(UUID sellerId, ListingStatus excludedStatus, Pageable pageable);

    List<Listing> findAllByIdInOrderByIdAsc(List<UUID> ids);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from Listing l where l.id = :id")
    Optional<Listing> findByIdForUpdate(@Param("id") UUID id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Listing l
               set l.status = :reserved,
                   l.reservationOrderId = :orderId,
                   l.reservationExpiresAt = :expiresAt,
                   l.updatedAt = :now,
                   l.version = l.version + 1
             where l.id = :listingId and l.status = :active
            """)
    int reserveIfActive(
            @Param("listingId") UUID listingId,
            @Param("orderId") UUID orderId,
            @Param("expiresAt") Instant expiresAt,
            @Param("now") Instant now,
            @Param("active") ListingStatus active,
            @Param("reserved") ListingStatus reserved
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Listing l
               set l.status = :active,
                   l.reservationOrderId = null,
                   l.reservationExpiresAt = null,
                   l.updatedAt = :now,
                   l.version = l.version + 1
             where l.id = :listingId and l.status = :reserved and l.reservationOrderId = :orderId
            """)
    int releaseIfReservedByOrder(
            @Param("listingId") UUID listingId,
            @Param("orderId") UUID orderId,
            @Param("now") Instant now,
            @Param("active") ListingStatus active,
            @Param("reserved") ListingStatus reserved
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Listing l
               set l.status = :sold,
                   l.reservationOrderId = null,
                   l.reservationExpiresAt = null,
                   l.updatedAt = :now,
                   l.version = l.version + 1
             where l.id = :listingId and l.status = :reserved and l.reservationOrderId = :orderId
            """)
    int markSoldIfReservedByOrder(
            @Param("listingId") UUID listingId,
            @Param("orderId") UUID orderId,
            @Param("now") Instant now,
            @Param("reserved") ListingStatus reserved,
            @Param("sold") ListingStatus sold
    );
}
