package com.kant.cardhub.catalog.infrastructure;

import com.kant.cardhub.catalog.domain.CardSet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CardSetRepository extends JpaRepository<CardSet, UUID> {
    List<CardSet> findAllByGameIdOrderByReleasedAtDescNameAsc(UUID gameId);
}
