package com.kant.cardhub.catalog.infrastructure;

import com.kant.cardhub.catalog.domain.Card;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface CardRepository extends JpaRepository<Card, UUID> {

    @Query("""
            select c from Card c
            where (:cardSetId is null or c.cardSetId = :cardSetId)
              and (:query is null or lower(c.name) like lower(concat('%', :query, '%'))
                   or lower(c.nameKo) like lower(concat('%', :query, '%'))
                   or lower(c.nameEn) like lower(concat('%', :query, '%'))
                   or lower(c.nameJa) like lower(concat('%', :query, '%'))
                   or lower(c.cardNumber) like lower(concat('%', :query, '%')))
            """)
    Page<Card> search(@Param("cardSetId") UUID cardSetId, @Param("query") String query, Pageable pageable);
}
