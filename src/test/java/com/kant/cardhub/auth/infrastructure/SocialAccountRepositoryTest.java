package com.kant.cardhub.auth.infrastructure;

import com.kant.cardhub.auth.domain.SocialAccount;
import com.kant.cardhub.auth.domain.SocialProvider;
import com.kant.cardhub.user.domain.User;
import com.kant.cardhub.user.domain.UserStatus;
import com.kant.cardhub.user.infrastructure.UserRepository;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class SocialAccountRepositoryTest {

    @Autowired
    SocialAccountRepository socialAccountRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    TransactionTemplate transactionTemplate;

    @Test
    void loadsUserBeforeRepositoryTransactionCloses() {
        String subject = "google-" + UUID.randomUUID();
        transactionTemplate.executeWithoutResult(status -> {
            User user = userRepository.save(new User("member@example.com", "member"));
            socialAccountRepository.save(new SocialAccount(user, SocialProvider.GOOGLE, subject));
        });

        User detachedUser = transactionTemplate.execute(status -> {
            SocialAccount account = socialAccountRepository
                    .findByProviderAndProviderSubject(SocialProvider.GOOGLE, subject)
                    .orElseThrow();
            assertThat(Hibernate.isInitialized(account.getUser())).isTrue();
            return account.getUser();
        });

        assertThat(detachedUser).isNotNull();
        assertThat(detachedUser.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }
}
