package com.kant.cardhub.user.application;

import com.kant.cardhub.auth.application.AuthSessionService;
import com.kant.cardhub.auth.infrastructure.SocialAccountRepository;
import com.kant.cardhub.common.error.BusinessException;
import com.kant.cardhub.common.error.ErrorCode;
import com.kant.cardhub.user.api.UpdateMeRequest;
import com.kant.cardhub.user.application.port.MarketplaceAccountLifecyclePort;
import com.kant.cardhub.user.domain.User;
import com.kant.cardhub.user.infrastructure.UserRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final SocialAccountRepository socialAccountRepository = mock(SocialAccountRepository.class);
    private final AuthSessionService authSessionService = mock(AuthSessionService.class);
    private final MarketplaceAccountLifecyclePort marketplaceLifecycle = mock(MarketplaceAccountLifecyclePort.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-08-25T00:00:00Z"), ZoneOffset.UTC);
    private final UserService service = new UserService(
            userRepository,
            socialAccountRepository,
            authSessionService,
            marketplaceLifecycle,
            clock
    );

    @Test
    void rejectsOnboardingForAUserYoungerThanFifteen() {
        UUID id = UUID.randomUUID();
        User user = new User("member@example.com", "member");
        when(userRepository.findById(id)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.update(
                id,
                new UpdateMeRequest(null, LocalDate.of(2012, 1, 1), true)
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.errorCode()).isEqualTo(ErrorCode.UNDERAGE));
    }

    @Test
    void acceptsOnboardingForAnAdultWhoAgreesToTerms() {
        UUID id = UUID.randomUUID();
        User user = new User("member@example.com", "member");
        when(userRepository.findById(id)).thenReturn(Optional.of(user));

        User updated = service.update(
                id,
                new UpdateMeRequest("new-name", LocalDate.of(2000, 1, 1), true)
        );

        assertThat(updated.onboardingCompleted()).isTrue();
        assertThat(updated.getNickname()).isEqualTo("new-name");
    }

    @Test
    void withdrawalLocksTheUserAndClosesMarketplaceState() {
        UUID id = UUID.randomUUID();
        User user = new User("member@example.com", "member");
        when(userRepository.findByIdForUpdate(id)).thenReturn(Optional.of(user));
        when(userRepository.hasActiveTransactions(id)).thenReturn(false);

        service.withdraw(id);

        verify(marketplaceLifecycle).withdraw(id, clock.instant());
        verify(authSessionService).revokeAll(id);
        verify(socialAccountRepository).deleteAllByUserId(id);
    }

    @Test
    void withdrawalWithAnActiveTransactionChangesNothing() {
        UUID id = UUID.randomUUID();
        User user = new User("member@example.com", "member");
        when(userRepository.findByIdForUpdate(id)).thenReturn(Optional.of(user));
        when(userRepository.hasActiveTransactions(id)).thenReturn(true);

        assertThatThrownBy(() -> service.withdraw(id))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.ACTIVE_TRANSACTION_EXISTS));

        verify(marketplaceLifecycle, never()).withdraw(id, clock.instant());
        verify(authSessionService, never()).revokeAll(id);
    }
}
