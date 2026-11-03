package com.kant.cardhub.auth.application;

import com.kant.cardhub.auth.domain.SocialProvider;
import com.kant.cardhub.auth.infrastructure.SocialAccountRepository;
import com.kant.cardhub.user.domain.User;
import com.kant.cardhub.user.infrastructure.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class OAuthUserProvisioningServiceTest {

    @Autowired OAuthUserProvisioningService provisioning;
    @Autowired SocialAccountRepository accounts;
    @Autowired UserRepository users;

    @Test
    void kakaoCreatesOneUserAndReusesItWithoutCollectingEmail() {
        String id = UUID.randomUUID().toString();
        OAuth2User principal = principal(Map.of(
                "id", id,
                "kakao_account", Map.of(
                        "email", "must-not-be-stored@example.com",
                        "profile", Map.of("nickname", "카카오 사용자"))
        ));
        long before = users.count();

        User first = provisioning.provision("kakao", principal);
        User again = provisioning.provision("kakao", principal);

        assertThat(again.getId()).isEqualTo(first.getId());
        assertThat(first.getNickname()).isEqualTo("카카오 사용자");
        assertThat(first.getEmail()).isNull();
        assertThat(users.count()).isEqualTo(before + 1);
        assertThat(accounts.findByProviderAndProviderSubject(SocialProvider.KAKAO, id))
                .get().extracting(account -> account.getUser().getId()).isEqualTo(first.getId());
    }

    @Test
    void missingNicknameUsesExistingDefaultAndIgnoresEmail() {
        User user = provisioning.provision("kakao", principal(Map.of(
                "id", UUID.randomUUID().toString(),
                "kakao_account", Map.of("email", "ignored@example.com")
        )));

        assertThat(user.getNickname()).isEqualTo("CardHub 사용자");
        assertThat(user.getEmail()).isNull();
    }

    @Test
    void missingKakaoIdIsRejectedWithoutCreatingAUser() {
        long before = users.count();

        assertThatThrownBy(() -> provisioning.provision("kakao", principal(Map.of(
                "kakao_account", Map.of("profile", Map.of("nickname", "카카오 사용자"))
        )))).isInstanceOf(IllegalArgumentException.class);
        assertThat(users.count()).isEqualTo(before);
    }

    @Test
    void kakaoDoesNotLinkAnExistingGoogleAccountByEmail() {
        String sharedEmail = "shared-" + UUID.randomUUID() + "@example.com";
        User google = provisioning.provision("google", principal(Map.of(
                "sub", UUID.randomUUID().toString(), "email", sharedEmail, "name", "Google 사용자")));
        User kakao = provisioning.provision("kakao", principal(Map.of(
                "id", UUID.randomUUID().toString(),
                "kakao_account", Map.of("email", sharedEmail, "profile", Map.of("nickname", "카카오 사용자")))));

        assertThat(kakao.getId()).isNotEqualTo(google.getId());
        assertThat(google.getEmail()).isEqualTo(sharedEmail);
        assertThat(kakao.getEmail()).isNull();
    }

    @Test
    void existingKakaoAccountRetainsItsPreviouslyStoredEmail() {
        String id = UUID.randomUUID().toString();
        User existing = users.save(new User("existing@example.com", "기존 사용자"));
        accounts.save(new com.kant.cardhub.auth.domain.SocialAccount(existing, SocialProvider.KAKAO, id));

        User result = provisioning.provision("kakao", principal(Map.of("id", id)));

        assertThat(result.getId()).isEqualTo(existing.getId());
        assertThat(result.getEmail()).isEqualTo("existing@example.com");
    }

    @Test
    void naverProfileStillUsesItsNestedResponse() {
        String id = UUID.randomUUID().toString();
        User user = provisioning.provision("naver", principal(Map.of("response", Map.of(
                "id", id, "email", "naver@example.com", "nickname", "네이버 사용자"))));

        assertThat(user.getEmail()).isEqualTo("naver@example.com");
        assertThat(user.getNickname()).isEqualTo("네이버 사용자");
        assertThat(accounts.findByProviderAndProviderSubject(SocialProvider.NAVER, id)).isPresent();
    }

    private OAuth2User principal(Map<String, Object> attributes) {
        OAuth2User principal = mock(OAuth2User.class);
        when(principal.getAttributes()).thenReturn(attributes);
        return principal;
    }
}
