package com.kant.cardhub.catalog.infrastructure;

import com.kant.cardhub.catalog.domain.CardGame;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CardGameRepository extends JpaRepository<CardGame, UUID> {
    Optional<CardGame> findBySlug(String slug);
}
