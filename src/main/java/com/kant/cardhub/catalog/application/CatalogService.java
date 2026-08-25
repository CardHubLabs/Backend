package com.kant.cardhub.catalog.application;

import com.kant.cardhub.catalog.domain.Card;
import com.kant.cardhub.catalog.domain.CardGame;
import com.kant.cardhub.catalog.domain.CardSet;
import com.kant.cardhub.catalog.domain.CardVariant;
import com.kant.cardhub.catalog.infrastructure.CardGameRepository;
import com.kant.cardhub.catalog.infrastructure.CardRepository;
import com.kant.cardhub.catalog.infrastructure.CardSetRepository;
import com.kant.cardhub.catalog.infrastructure.CardVariantRepository;
import com.kant.cardhub.common.error.BusinessException;
import com.kant.cardhub.common.error.ErrorCode;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class CatalogService {

    private final CardGameRepository gameRepository;
    private final CardSetRepository setRepository;
    private final CardRepository cardRepository;
    private final CardVariantRepository variantRepository;
    private final Clock clock;

    public CatalogService(
            CardGameRepository gameRepository,
            CardSetRepository setRepository,
            CardRepository cardRepository,
            CardVariantRepository variantRepository,
            Clock clock
    ) {
        this.gameRepository = gameRepository;
        this.setRepository = setRepository;
        this.cardRepository = cardRepository;
        this.variantRepository = variantRepository;
        this.clock = clock;
    }

    public List<CatalogView.Game> games() {
        return gameRepository.findAll(Sort.by("name")).stream().map(CatalogService::toGame).toList();
    }

    public List<CatalogView.Set> sets(UUID gameId) {
        requireGame(gameId);
        return setRepository.findAllByGameIdOrderByReleasedAtDescNameAsc(gameId).stream()
                .map(CatalogService::toSet)
                .toList();
    }

    public CatalogView.Page<CatalogView.CardSummary> cards(UUID setId, String query, int page, int size) {
        if (setId != null && !setRepository.existsById(setId)) throw notFound("카드 세트를 찾을 수 없습니다.");
        String normalized = query == null || query.isBlank() ? null : query.trim();
        var result = cardRepository.search(
                setId,
                normalized,
                PageRequest.of(page, size, Sort.by("name").ascending().and(Sort.by("cardNumber").ascending()))
        );
        return new CatalogView.Page<>(
                result.getContent().stream().map(CatalogService::toCardSummary).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages()
        );
    }

    public CatalogView.CardDetail card(UUID cardId) {
        Card card = cardRepository.findById(cardId).orElseThrow(() -> notFound("카드를 찾을 수 없습니다."));
        List<CatalogView.Variant> variants = variantRepository.findAllByCardIdOrderByLanguageAscVersionAsc(cardId)
                .stream().map(CatalogService::toVariant).toList();
        return new CatalogView.CardDetail(
                card.getId(), card.getCardSetId(), card.getName(), card.getCardNumber(), variants
        );
    }

    public CatalogView.Variant variant(UUID variantId) {
        return variantRepository.findById(variantId)
                .map(CatalogService::toVariant)
                .orElseThrow(() -> notFound("카드 variant를 찾을 수 없습니다."));
    }

    public boolean variantExists(UUID variantId) {
        return variantRepository.existsById(variantId);
    }

    @Transactional
    public CatalogView.Game createGame(String name, String slug) {
        if (gameRepository.findBySlug(slug.trim().toLowerCase(Locale.ROOT)).isPresent()) {
            throw new BusinessException(ErrorCode.CONFLICT, "이미 사용 중인 게임 slug입니다.");
        }
        return toGame(gameRepository.save(CardGame.create(name, slug, clock.instant())));
    }

    @Transactional
    public CatalogView.Set createSet(UUID gameId, String name, String code, LocalDate releasedAt) {
        requireGame(gameId);
        return toSet(setRepository.save(CardSet.create(gameId, name, code, releasedAt, clock.instant())));
    }

    @Transactional
    public CatalogView.CardSummary createCard(UUID setId, String name, String cardNumber) {
        if (!setRepository.existsById(setId)) throw notFound("카드 세트를 찾을 수 없습니다.");
        return toCardSummary(cardRepository.save(Card.create(setId, name, cardNumber, clock.instant())));
    }

    @Transactional
    public CatalogView.Variant createVariant(
            UUID cardId, String language, String version, String rarity, String imageUrl
    ) {
        if (!cardRepository.existsById(cardId)) throw notFound("카드를 찾을 수 없습니다.");
        return toVariant(variantRepository.save(
                CardVariant.create(cardId, language, version, rarity, imageUrl, clock.instant())
        ));
    }

    private void requireGame(UUID id) {
        if (!gameRepository.existsById(id)) throw notFound("카드 게임을 찾을 수 없습니다.");
    }

    private static BusinessException notFound(String message) {
        return new BusinessException(ErrorCode.CATALOG_NOT_FOUND, message);
    }

    private static CatalogView.Game toGame(CardGame game) {
        return new CatalogView.Game(game.getId(), game.getName(), game.getSlug());
    }

    private static CatalogView.Set toSet(CardSet set) {
        return new CatalogView.Set(set.getId(), set.getGameId(), set.getName(), set.getCode(), set.getReleasedAt());
    }

    private static CatalogView.CardSummary toCardSummary(Card card) {
        return new CatalogView.CardSummary(card.getId(), card.getCardSetId(), card.getName(), card.getCardNumber());
    }

    private static CatalogView.Variant toVariant(CardVariant variant) {
        return new CatalogView.Variant(
                variant.getId(), variant.getLanguage(), variant.getVersion(), variant.getRarity(), variant.getImageUrl()
        );
    }
}
