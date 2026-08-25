package com.kant.cardhub.auth.infrastructure;

import com.kant.cardhub.auth.domain.SocialAccount;
import com.kant.cardhub.auth.domain.SocialProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SocialAccountRepository extends JpaRepository<SocialAccount, UUID> {

    Optional<SocialAccount> findByProviderAndProviderSubject(SocialProvider provider, String providerSubject);

    void deleteAllByUserId(UUID userId);
}
