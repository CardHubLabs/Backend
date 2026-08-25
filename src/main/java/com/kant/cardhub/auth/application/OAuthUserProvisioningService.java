package com.kant.cardhub.auth.application;

import com.kant.cardhub.auth.domain.SocialAccount;
import com.kant.cardhub.auth.domain.SocialProvider;
import com.kant.cardhub.auth.infrastructure.SocialAccountRepository;
import com.kant.cardhub.user.domain.User;
import com.kant.cardhub.user.infrastructure.UserRepository;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class OAuthUserProvisioningService {

    private final SocialAccountRepository socialAccountRepository;
    private final UserRepository userRepository;

    public OAuthUserProvisioningService(
            SocialAccountRepository socialAccountRepository,
            UserRepository userRepository
    ) {
        this.socialAccountRepository = socialAccountRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public User provision(String registrationId, OAuth2User principal) {
        SocialProvider provider = SocialProvider.fromRegistrationId(registrationId);
        OAuthProfile profile = profile(provider, principal.getAttributes());
        return socialAccountRepository.findByProviderAndProviderSubject(provider, profile.subject())
                .map(SocialAccount::getUser)
                .orElseGet(() -> create(provider, profile));
    }

    private User create(SocialProvider provider, OAuthProfile profile) {
        User user = userRepository.save(new User(profile.email(), profile.nickname()));
        socialAccountRepository.save(new SocialAccount(user, provider, profile.subject()));
        return user;
    }

    @SuppressWarnings("unchecked")
    private OAuthProfile profile(SocialProvider provider, Map<String, Object> attributes) {
        return switch (provider) {
            case GOOGLE -> new OAuthProfile(
                    string(attributes.get("sub"), attributes.get("id")),
                    nullableString(attributes.get("email")),
                    string(attributes.get("name"), attributes.get("email"))
            );
            case KAKAO -> {
                Map<String, Object> account = map(attributes.get("kakao_account"));
                Map<String, Object> properties = map(attributes.get("properties"));
                Map<String, Object> profile = map(account.get("profile"));
                yield new OAuthProfile(
                        string(attributes.get("id")),
                        nullableString(account.get("email")),
                        string(profile.get("nickname"), properties.get("nickname"), account.get("email"))
                );
            }
            case NAVER -> {
                Map<String, Object> response = map(attributes.get("response"));
                yield new OAuthProfile(
                        string(response.get("id")),
                        nullableString(response.get("email")),
                        string(response.get("nickname"), response.get("name"), response.get("email"))
                );
            }
        };
    }

    private Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private String string(Object... candidates) {
        for (Object candidate : candidates) {
            if (candidate != null && !candidate.toString().isBlank()) {
                return candidate.toString();
            }
        }
        throw new IllegalArgumentException("OAuth provider did not return a required subject");
    }

    private String nullableString(Object value) {
        return value == null || value.toString().isBlank() ? null : value.toString();
    }

    private record OAuthProfile(String subject, String email, String nickname) {
    }
}
