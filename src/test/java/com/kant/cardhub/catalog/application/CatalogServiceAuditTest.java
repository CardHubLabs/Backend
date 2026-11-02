package com.kant.cardhub.catalog.application;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.kant.cardhub.catalog.domain.Card;
import com.kant.cardhub.catalog.domain.CardGame;
import com.kant.cardhub.catalog.domain.CardSet;
import com.kant.cardhub.catalog.domain.CardVariant;
import com.kant.cardhub.catalog.domain.GameCode;
import com.kant.cardhub.catalog.domain.PrintType;
import com.kant.cardhub.catalog.domain.VersionType;
import com.kant.cardhub.catalog.infrastructure.CardGameRepository;
import com.kant.cardhub.catalog.infrastructure.CardRepository;
import com.kant.cardhub.catalog.infrastructure.CardSetRepository;
import com.kant.cardhub.catalog.infrastructure.CardVariantRepository;
import com.kant.cardhub.common.error.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CatalogServiceAuditTest {

    private static final Instant NOW = Instant.parse("2026-09-02T00:00:00Z");

    @Mock private CardGameRepository gameRepository;
    @Mock private CardSetRepository setRepository;
    @Mock private CardRepository cardRepository;
    @Mock private CardVariantRepository variantRepository;

    private CatalogService service;
    private Logger auditLogger;
    private ListAppender<ILoggingEvent> auditAppender;

    @BeforeEach
    void setUp() {
        service = new CatalogService(
                gameRepository,
                setRepository,
                cardRepository,
                variantRepository,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        auditLogger = (Logger) LoggerFactory.getLogger("CARDHUB_ADMIN_AUDIT");
        auditAppender = new ListAppender<>();
        auditAppender.start();
        auditLogger.addAppender(auditAppender);
    }

    @AfterEach
    void tearDown() {
        auditLogger.detachAppender(auditAppender);
        auditAppender.stop();
    }

    @Test
    void allCatalogMutationsWriteExactlyOneAuditEventWithActorActionAndTarget() {
        UUID adminId = UUID.randomUUID();
        CardGame existingGame = CardGame.create("Existing Game", "existing-game", GameCode.POKEMON, NOW);
        CardSet existingSet = CardSet.create(
                existingGame.getId(), "Existing Set", "EX-01", LocalDate.of(2026, 1, 1), NOW
        );
        Card existingCard = Card.create(
                existingSet.getId(), "Existing Card", "001", null, "Existing Card", null, "RARE", NOW
        );
        CardVariant existingVariant = CardVariant.create(
                existingCard.getId(), "EN", "First", "RARE", "https://example.invalid/original.webp",
                VersionType.NORMAL, PrintType.NORMAL, true, NOW
        );

        when(gameRepository.findBySlug("one-piece")).thenReturn(Optional.empty());
        when(gameRepository.findByCode(GameCode.ONE_PIECE)).thenReturn(Optional.empty());
        when(gameRepository.save(any(CardGame.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(gameRepository.existsById(existingGame.getId())).thenReturn(true);
        when(setRepository.save(any(CardSet.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(setRepository.existsById(existingSet.getId())).thenReturn(true);
        when(cardRepository.save(any(Card.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(cardRepository.existsById(existingCard.getId())).thenReturn(true);
        when(variantRepository.save(any(CardVariant.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(gameRepository.findById(existingGame.getId())).thenReturn(Optional.of(existingGame));
        when(setRepository.findById(existingSet.getId())).thenReturn(Optional.of(existingSet));
        when(cardRepository.findById(existingCard.getId())).thenReturn(Optional.of(existingCard));
        when(variantRepository.findById(existingVariant.getId())).thenReturn(Optional.of(existingVariant));

        CatalogView.Game createdGame = service.createGame(
                adminId, "One Piece", "one-piece", GameCode.ONE_PIECE
        );
        CatalogView.Set createdSet = service.createSet(
                adminId, existingGame.getId(), "New Set", "NEW-01", LocalDate.of(2026, 9, 2)
        );
        CatalogView.CardSummary createdCard = service.createCard(
                adminId, existingSet.getId(), "Secret Card Name", "999", "비밀 카드", "Secret Card Name",
                "秘密カード", "SECRET"
        );
        CatalogView.Variant createdVariant = service.createVariant(
                adminId, existingCard.getId(), "JA", "Manga", "SECRET",
                "https://example.invalid/secret-image.webp", VersionType.MANGA, PrintType.HOLO, true
        );
        service.updateGame(adminId, existingGame.getId(), "Updated Game", "updated-game", GameCode.POKEMON);
        service.updateSet(
                adminId, existingSet.getId(), existingGame.getId(), "Updated Set", "UP-01",
                LocalDate.of(2026, 2, 1)
        );
        service.updateCard(
                adminId, existingCard.getId(), existingSet.getId(), "Updated Card", "002",
                "수정 카드", "Updated Card", null, "SUPER_RARE"
        );
        service.updateVariant(
                adminId, existingVariant.getId(), "KO", "Promo", "PROMO",
                "https://example.invalid/updated.webp", VersionType.PROMO, PrintType.HOLO, true
        );
        service.setVariantListable(adminId, existingVariant.getId(), false);

        List<String> messages = auditMessages();
        assertThat(messages).containsExactly(
                auditMessage(adminId, "CATALOG_GAME_CREATE", createdGame.id()),
                auditMessage(adminId, "CATALOG_SET_CREATE", createdSet.id()),
                auditMessage(adminId, "CATALOG_CARD_CREATE", createdCard.id()),
                auditMessage(adminId, "CATALOG_VARIANT_CREATE", createdVariant.id()),
                auditMessage(adminId, "CATALOG_GAME_UPDATE", existingGame.getId()),
                auditMessage(adminId, "CATALOG_SET_UPDATE", existingSet.getId()),
                auditMessage(adminId, "CATALOG_CARD_UPDATE", existingCard.getId()),
                auditMessage(adminId, "CATALOG_VARIANT_UPDATE", existingVariant.getId()),
                auditMessage(adminId, "CATALOG_VARIANT_LISTABILITY_UPDATE", existingVariant.getId())
                        + " listable=false"
        );
        assertThat(messages).allSatisfy(message -> {
            assertThat(message).doesNotContain("Secret Card Name");
            assertThat(message).doesNotContain("secret-image.webp");
        });
    }

    @Test
    void failedCatalogMutationDoesNotWriteAuditEvent() {
        UUID adminId = UUID.randomUUID();
        CardGame duplicate = CardGame.create("Duplicate", "one-piece", GameCode.ONE_PIECE, NOW);
        when(gameRepository.findBySlug("one-piece")).thenReturn(Optional.of(duplicate));

        assertThatThrownBy(() -> service.createGame(
                adminId, "Secret Duplicate Name", "one-piece", GameCode.ONE_PIECE
        )).isInstanceOf(BusinessException.class);

        assertThat(auditMessages()).isEmpty();
        verifyNoInteractions(setRepository, cardRepository, variantRepository);
    }

    private List<String> auditMessages() {
        return auditAppender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    private static String auditMessage(UUID actorId, String action, UUID targetId) {
        return "actorId=" + actorId + " action=" + action + " targetId=" + targetId;
    }
}
