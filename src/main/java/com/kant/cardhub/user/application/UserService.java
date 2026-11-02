package com.kant.cardhub.user.application;

import com.kant.cardhub.auth.application.AuthSessionService;
import com.kant.cardhub.auth.infrastructure.SocialAccountRepository;
import com.kant.cardhub.common.error.BusinessException;
import com.kant.cardhub.common.error.ErrorCode;
import com.kant.cardhub.user.api.UpdateMeRequest;
import com.kant.cardhub.user.application.port.MarketplaceAccountLifecyclePort;
import com.kant.cardhub.user.domain.User;
import com.kant.cardhub.user.domain.UserStatus;
import com.kant.cardhub.user.infrastructure.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.UUID;

@Service
public class UserService {

    private static final int MINIMUM_AGE = 15;

    private final UserRepository userRepository;
    private final SocialAccountRepository socialAccountRepository;
    private final AuthSessionService authSessionService;
    private final MarketplaceAccountLifecyclePort marketplaceLifecycle;
    private final Clock clock;

    public UserService(
            UserRepository userRepository,
            SocialAccountRepository socialAccountRepository,
            AuthSessionService authSessionService,
            MarketplaceAccountLifecyclePort marketplaceLifecycle,
            Clock clock
    ) {
        this.userRepository = userRepository;
        this.socialAccountRepository = socialAccountRepository;
        this.authSessionService = authSessionService;
        this.marketplaceLifecycle = marketplaceLifecycle;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public User get(UUID userId) {
        return activeUser(userId);
    }

    @Transactional
    public User update(UUID userId, UpdateMeRequest request) {
        User user = activeUser(userId);
        if (request.birthDate() != null) {
            validateAge(request.birthDate());
        }
        Instant termsAgreedAt = Boolean.TRUE.equals(request.termsAccepted()) ? clock.instant() : null;
        if (Boolean.FALSE.equals(request.termsAccepted()) && user.getTermsAgreedAt() == null) {
            throw new BusinessException(ErrorCode.ONBOARDING_REQUIRED, "서비스 이용약관 동의가 필요합니다.");
        }
        user.updateProfile(request.nickname(), request.birthDate(), termsAgreedAt);
        return user;
    }

    @Transactional
    public void withdraw(UUID userId) {
        User user = activeUserForUpdate(userId);
        if (userRepository.hasActiveTransactions(userId)) {
            throw new BusinessException(ErrorCode.ACTIVE_TRANSACTION_EXISTS);
        }
        Instant now = clock.instant();
        marketplaceLifecycle.withdraw(userId, now);
        authSessionService.revokeAll(userId);
        socialAccountRepository.deleteAllByUserId(userId);
        user.withdraw(now);
    }

    private User activeUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (user.getStatus() == UserStatus.SUSPENDED) {
            throw new BusinessException(ErrorCode.USER_SUSPENDED);
        }
        if (user.getStatus() == UserStatus.WITHDRAWN) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        return user;
    }

    private User activeUserForUpdate(UUID userId) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (user.getStatus() == UserStatus.SUSPENDED) {
            throw new BusinessException(ErrorCode.USER_SUSPENDED);
        }
        if (user.getStatus() == UserStatus.WITHDRAWN) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        return user;
    }

    private void validateAge(LocalDate birthDate) {
        LocalDate today = LocalDate.now(clock);
        if (birthDate.isAfter(today) || Period.between(birthDate, today).getYears() < MINIMUM_AGE) {
            throw new BusinessException(ErrorCode.UNDERAGE);
        }
    }
}
