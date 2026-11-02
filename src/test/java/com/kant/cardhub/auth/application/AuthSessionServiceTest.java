package com.kant.cardhub.auth.application;

import com.kant.cardhub.auth.config.AuthProperties;
import com.kant.cardhub.auth.domain.AuthSession;
import com.kant.cardhub.auth.infrastructure.AuthSessionRepository;
import com.kant.cardhub.common.error.BusinessException;
import com.kant.cardhub.common.error.ErrorCode;
import com.kant.cardhub.user.domain.User;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthSessionServiceTest {

    private final Instant now = Instant.parse("2026-08-25T00:00:00Z");
    private final AuthSessionRepository repository = mock(AuthSessionRepository.class);
    private final JwtTokenService jwtTokenService = mock(JwtTokenService.class);
    private final AuthProperties properties = new AuthProperties(
            "01234567890123456789012345678901",
            Duration.ofMinutes(30),
            Duration.ofDays(14),
            "cardhub_refresh",
            false,
            "http://localhost/callback"
    );
    private final AuthSessionService service = new AuthSessionService(
            repository,
            jwtTokenService,
            properties,
            Clock.fixed(now, ZoneOffset.UTC)
    );

    @Test
    void createsOpaqueRefreshSessionAndAccessToken() {
        User user = new User("member@example.com", "member");
        when(repository.save(any(AuthSession.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtTokenService.issue(user)).thenReturn(
                new JwtTokenService.AccessToken("access-token", now.plus(Duration.ofMinutes(30)))
        );

        AuthTokens tokens = service.create(user, "test-agent", "127.0.0.1");

        assertThat(tokens.accessToken()).isEqualTo("access-token");
        assertThat(tokens.refreshToken()).isNotBlank().doesNotContain(".");
        verify(repository).save(any(AuthSession.class));
    }

    @Test
    void refreshTokenReuseRevokesEveryActiveSessionForTheUser() {
        User user = new User("member@example.com", "member");
        AuthSession reused = new AuthSession(
                user,
                TokenHash.sha256("reused-token"),
                now.plus(Duration.ofDays(1)),
                null,
                null
        );
        reused.revoke(now.minusSeconds(1));
        when(repository.findByRefreshTokenHashForUpdate(TokenHash.sha256("reused-token")))
                .thenReturn(Optional.of(reused));

        assertThatThrownBy(() -> service.reissue("reused-token", null, null))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.REFRESH_TOKEN_REUSED));
        verify(repository).revokeAllActiveByUserId(user.getId(), now);
    }

    @Test
    void rotatesRefreshTokenAndReuseOfThePreviousTokenRevokesAllSessions() {
        User user = new User("member@example.com", "member");
        String previousToken = "previous-refresh-token";
        String previousHash = TokenHash.sha256(previousToken);
        AuthSession previousSession = new AuthSession(
                user,
                previousHash,
                now.plus(Duration.ofDays(1)),
                "old-agent",
                TokenHash.sha256("127.0.0.1")
        );
        AtomicReference<AuthSession> replacement = new AtomicReference<>();

        when(repository.save(any(AuthSession.class))).thenAnswer(invocation -> {
            AuthSession saved = invocation.getArgument(0);
            replacement.set(saved);
            return saved;
        });
        when(repository.findByRefreshTokenHashForUpdate(anyString())).thenAnswer(invocation -> {
            String requestedHash = invocation.getArgument(0);
            if (previousHash.equals(requestedHash)) {
                return Optional.of(previousSession);
            }
            return Optional.ofNullable(replacement.get());
        });
        when(jwtTokenService.issue(user)).thenReturn(
                new JwtTokenService.AccessToken("rotated-access-token", now.plus(Duration.ofMinutes(30)))
        );

        AuthTokens rotated = service.reissue(previousToken, "new-agent", "10.0.0.1");

        assertThat(rotated.accessToken()).isEqualTo("rotated-access-token");
        assertThat(rotated.refreshToken()).isNotBlank().isNotEqualTo(previousToken);
        assertThat(previousSession.revoked()).isTrue();
        verify(repository).findByRefreshTokenHashForUpdate(TokenHash.sha256(rotated.refreshToken()));

        assertThatThrownBy(() -> service.reissue(previousToken, "attacker-agent", "10.0.0.2"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.REFRESH_TOKEN_REUSED));
        verify(repository).revokeAllActiveByUserId(user.getId(), now);
    }
}
