package com.kant.cardhub.catalog.infrastructure;

import com.kant.cardhub.catalog.domain.CardVariant;
import com.kant.cardhub.catalog.domain.GameCode;
import com.kant.cardhub.catalog.domain.CardLanguage;
import com.kant.cardhub.catalog.domain.PrintType;
import com.kant.cardhub.catalog.domain.VersionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface CardVariantRepository extends JpaRepository<CardVariant, UUID> {
    List<CardVariant> findAllByCardIdOrderByLanguageAscVersionAsc(UUID cardId);

    @Query(value = """
            select v
              from CardVariant v, Card c, CardSet s, CardGame g
             where v.cardId = c.id
               and c.cardSetId = s.id
               and s.gameId = g.id
               and (:gameId is null or g.id = :gameId)
               and (:gameCode is null or g.code = :gameCode)
               and (:setId is null or s.id = :setId)
               and (:cardId is null or c.id = :cardId)
               and (:language is null or v.language = :language)
               and (:versionType is null or v.versionType = :versionType)
               and (:printType is null or v.printType = :printType)
               and (:listable is null or v.listable = :listable)
               and (:rarity is null or lower(c.rarity) = lower(:rarity))
               and (:legacyVariantRarity is null or lower(v.rarity) = lower(:legacyVariantRarity))
               and (:query is null
                    or lower(c.name) like lower(concat('%', :query, '%'))
                    or lower(c.nameKo) like lower(concat('%', :query, '%'))
                    or lower(c.nameEn) like lower(concat('%', :query, '%'))
                    or lower(c.nameJa) like lower(concat('%', :query, '%'))
                    or lower(c.cardNumber) like lower(concat('%', :query, '%')))
             order by c.name asc, c.cardNumber asc, v.language asc, v.version asc, v.id asc
            """,
            countQuery = """
            select count(v)
              from CardVariant v, Card c, CardSet s, CardGame g
             where v.cardId = c.id
               and c.cardSetId = s.id
               and s.gameId = g.id
               and (:gameId is null or g.id = :gameId)
               and (:gameCode is null or g.code = :gameCode)
               and (:setId is null or s.id = :setId)
               and (:cardId is null or c.id = :cardId)
               and (:language is null or v.language = :language)
               and (:versionType is null or v.versionType = :versionType)
               and (:printType is null or v.printType = :printType)
               and (:listable is null or v.listable = :listable)
               and (:rarity is null or lower(c.rarity) = lower(:rarity))
               and (:legacyVariantRarity is null or lower(v.rarity) = lower(:legacyVariantRarity))
               and (:query is null
                    or lower(c.name) like lower(concat('%', :query, '%'))
                    or lower(c.nameKo) like lower(concat('%', :query, '%'))
                    or lower(c.nameEn) like lower(concat('%', :query, '%'))
                    or lower(c.nameJa) like lower(concat('%', :query, '%'))
                    or lower(c.cardNumber) like lower(concat('%', :query, '%')))
            """)
    Page<CardVariant> search(
            @Param("gameId") UUID gameId,
            @Param("gameCode") GameCode gameCode,
            @Param("setId") UUID setId,
            @Param("cardId") UUID cardId,
            @Param("language") CardLanguage language,
            @Param("versionType") VersionType versionType,
            @Param("printType") PrintType printType,
            @Param("listable") Boolean listable,
            @Param("rarity") String rarity,
            @Param("legacyVariantRarity") String legacyVariantRarity,
            @Param("query") String query,
            Pageable pageable
    );
}
