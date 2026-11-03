package com.kant.cardhub.auth.config;

import com.kant.cardhub.auth.api.AuthCookieService;
import com.kant.cardhub.auth.application.AuthSessionService;
import com.kant.cardhub.auth.application.AuthTokens;
import com.kant.cardhub.auth.application.OAuthUserProvisioningService;
import com.kant.cardhub.common.error.BusinessException;
import com.kant.cardhub.common.error.ErrorCode;
import com.kant.cardhub.user.domain.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OAuthLoginHandlersTest {

    private final AuthProperties properties = new AuthProperties(
            "01234567890123456789012345678901", Duration.ofMinutes(30), Duration.ofDays(14),
            "cardhub_refresh", false, "http://localhost:3000/auth/callback");
    private final OAuthLoginRedirect redirect = new OAuthLoginRedirect(properties);
    private final OAuthUserProvisioningService provisioning = mock(OAuthUserProvisioningService.class);
    private final AuthSessionService sessions = mock(AuthSessionService.class);
    private final OAuthLoginSuccessHandler success = new OAuthLoginSuccessHandler(
            provisioning, sessions, new AuthCookieService(properties), redirect);
    private final OAuthLoginFailureHandler failure = new OAuthLoginFailureHandler(redirect);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void successfulKakaoLoginIssuesRefreshCookieAndRedirects() throws Exception {
        OAuth2AuthenticationToken authentication = authentication();
        User user = new User(null, "카카오 사용자");
        when(provisioning.provision("kakao", authentication.getPrincipal())).thenReturn(user);
        when(sessions.create(any(), any(), any())).thenReturn(new AuthTokens("access", Instant.now(), "refresh"));
        MockHttpServletRequest request = request();
        MockHttpServletResponse response = new MockHttpServletResponse();
        SecurityContextHolder.getContext().setAuthentication(authentication);

        success.onAuthenticationSuccess(request, response, authentication);

        assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:3000/auth/callback");
        assertThat(response.getHeader("Set-Cookie")).contains("cardhub_refresh=refresh", "HttpOnly", "SameSite=Lax");
        assertThat(request.getSession(false)).isNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void suspendedAndWithdrawnAccountsDoNotReceiveRefreshCookies() throws Exception {
        for (ErrorCode code : new ErrorCode[]{ErrorCode.USER_SUSPENDED, ErrorCode.UNAUTHORIZED}) {
            OAuth2AuthenticationToken authentication = authentication();
            User user = new User(null, "사용자");
            when(provisioning.provision("kakao", authentication.getPrincipal())).thenReturn(user);
            doThrow(new BusinessException(code)).when(sessions).create(any(), any(), any());
            MockHttpServletRequest request = request();
            MockHttpServletResponse response = new MockHttpServletResponse();

            success.onAuthenticationSuccess(request, response, authentication);

            assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:3000/auth/callback?error=account_unavailable");
            assertThat(response.getHeader("Set-Cookie")).isNull();
            assertThat(request.getSession(false)).isNull();
        }
    }

    @Test
    void invalidProviderDataRedirectsWithoutExposingDetailsOrIssuingCookie() throws Exception {
        when(provisioning.provision(anyString(), any())).thenThrow(new IllegalArgumentException("private provider data"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        success.onAuthenticationSuccess(request(), response, authentication());

        assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:3000/auth/callback?error=oauth_failed");
        assertThat(response.getHeader("Set-Cookie")).isNull();
    }

    @Test
    void cancellationAndUnknownFailuresExposeOnlyStableCodes() throws Exception {
        MockHttpServletResponse cancelled = new MockHttpServletResponse();
        failure.onAuthenticationFailure(request(), cancelled,
                new OAuth2AuthenticationException(new OAuth2Error("access_denied", "private description", null)));
        assertThat(cancelled.getRedirectedUrl()).isEqualTo("http://localhost:3000/auth/callback?error=access_denied");

        MockHttpServletResponse unknown = new MockHttpServletResponse();
        failure.onAuthenticationFailure(request(), unknown, new AuthenticationServiceException("private token"));
        assertThat(unknown.getRedirectedUrl()).isEqualTo("http://localhost:3000/auth/callback?error=oauth_failed");
        assertThat(unknown.getHeader("Set-Cookie")).isNull();
    }

    private OAuth2AuthenticationToken authentication() {
        var principal = new DefaultOAuth2User(Set.of(new SimpleGrantedAuthority("ROLE_USER")), Map.of("id", 123L), "id");
        return new OAuth2AuthenticationToken(principal, principal.getAuthorities(), "kakao");
    }

    private MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(new MockHttpSession());
        return request;
    }
}
