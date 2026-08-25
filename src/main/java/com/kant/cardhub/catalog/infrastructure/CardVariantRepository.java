package com.kant.cardhub.catalog.infrastructure;

import com.kant.cardhub.catalog.domain.CardVariant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CardVariantRepository extends JpaRepository<CardVariant, UUID> {
    List<CardVariant> findAllByCardIdOrderByLanguageAscVersionAsc(UUID cardId);
}
