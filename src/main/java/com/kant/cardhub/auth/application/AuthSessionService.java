package com.kant.cardhub.auth.application;

import com.kant.cardhub.auth.config.AuthProperties;
import com.kant.cardhub.auth.domain.AuthSession;
import com.kant.cardhub.auth.infrastructure.AuthSessionRepository;
import com.kant.cardhub.common.error.BusinessException;
import com.kant.cardhub.common.error.ErrorCode;
import com.kant.cardhub.user.domain.User;
import com.kant.cardhub.user.domain.UserStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

@Service
public class AuthSessionService {

    private final AuthSessionRepository sessionRepository;
    private final JwtTokenService jwtTokenService;
    private final AuthProperties properties;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthSessionService(
            AuthSessionRepository sessionRepository,
            JwtTokenService jwtTokenService,
            AuthProperties properties,
            Clock clock
    ) {
        this.sessionRepository = sessionRepository;
        this.jwtTokenService = jwtTokenService;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public AuthTokens create(User user, String userAgent, String remoteAddress) {
        ensureLoginAllowed(user);
        return createSession(user, userAgent, remoteAddress);
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public AuthTokens reissue(String rawRefreshToken, String userAgent, String remoteAddress) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
        Instant now = clock.instant();
        AuthSession current = sessionRepository.findByRefreshTokenHashForUpdate(TokenHash.sha256(rawRefreshToken))
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));
        if (current.revoked()) {
            sessionRepository.revokeAllActiveByUserId(current.getUser().getId(), now);
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_REUSED);
        }
        if (current.expiredAt(now)) {
            current.revoke(now);
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN, "만료된 refresh token입니다.");
        }
        ensureLoginAllowed(current.getUser());

        AuthTokens tokens = createSession(current.getUser(), userAgent, remoteAddress);
        AuthSession replacement = sessionRepository.findByRefreshTokenHashForUpdate(TokenHash.sha256(tokens.refreshToken()))
                .orElseThrow(() -> new IllegalStateException("Replacement session was not saved"));
        current.rotate(replacement.getId(), now);
        return tokens;
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        sessionRepository.findByRefreshTokenHashForUpdate(TokenHash.sha256(rawRefreshToken))
                .ifPresent(session -> session.revoke(clock.instant()));
    }

    @Transactional
    public void revokeAll(UUID userId) {
        sessionRepository.revokeAllActiveByUserId(userId, clock.instant());
    }

    private AuthTokens createSession(User user, String userAgent, String remoteAddress) {
        Instant now = clock.instant();
        String refreshToken = newRefreshToken();
        AuthSession session = new AuthSession(
                user,
                TokenHash.sha256(refreshToken),
                now.plus(properties.refreshTokenTtl()),
                userAgent,
                remoteAddress == null ? null : TokenHash.sha256(remoteAddress)
        );
        sessionRepository.save(session);
        JwtTokenService.AccessToken accessToken = jwtTokenService.issue(user);
        return new AuthTokens(accessToken.value(), accessToken.expiresAt(), refreshToken);
    }

    private String newRefreshToken() {
        byte[] value = new byte[32];
        secureRandom.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private void ensureLoginAllowed(User user) {
        if (user.getStatus() == UserStatus.SUSPENDED) {
            throw new BusinessException(ErrorCode.USER_SUSPENDED);
        }
        if (user.getStatus() == UserStatus.WITHDRAWN) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
    }
}
