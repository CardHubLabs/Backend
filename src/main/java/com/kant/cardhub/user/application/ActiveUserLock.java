package com.kant.cardhub.user.application;

import com.kant.cardhub.common.error.BusinessException;
import com.kant.cardhub.common.error.ErrorCode;
import com.kant.cardhub.user.domain.User;
import com.kant.cardhub.user.domain.UserStatus;
import com.kant.cardhub.user.infrastructure.UserRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class ActiveUserLock {

    private final UserRepository userRepository;

    public ActiveUserLock(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public void requireActive(UUID userId) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (user.getStatus() == UserStatus.SUSPENDED) {
            throw new BusinessException(ErrorCode.USER_SUSPENDED);
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
    }
}
