package com.kant.cardhub.alert.infrastructure;

import com.kant.cardhub.alert.domain.PriceAlert;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PriceAlertRepository extends JpaRepository<PriceAlert, UUID> {

    long countByUserIdAndActiveTrue(UUID userId);

    boolean existsByUserIdAndListingIdAndActiveTrue(UUID userId, UUID listingId);

    Optional<PriceAlert> findByIdAndUserId(UUID id, UUID userId);

    Page<PriceAlert> findAllByUserId(UUID userId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<PriceAlert> findAllByListingIdAndActiveTrueOrderByCreatedAtAsc(UUID listingId);
}

