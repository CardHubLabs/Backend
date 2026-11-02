package com.kant.cardhub.auth.api;

import com.kant.cardhub.auth.config.AuthProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class AuthCookieServiceTest {

    private final AuthCookieService cookieService = new AuthCookieService(new AuthProperties(
            "01234567890123456789012345678901",
            Duration.ofMinutes(30),
            Duration.ofDays(14),
            "cardhub_refresh",
            true,
            "https://cardhub.example/auth/callback"
    ));

    @Test
    void writesRefreshTokenOnlyToARestrictedSecureHttpOnlyCookie() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        cookieService.write(response, "opaque-refresh-token");

        assertThat(response.getHeader(HttpHeaders.SET_COOKIE))
                .contains("cardhub_refresh=opaque-refresh-token")
                .contains("Path=/api/v1/auth")
                .contains("Max-Age=1209600")
                .contains("Secure")
                .contains("HttpOnly")
                .contains("SameSite=Lax");
    }

    @Test
    void clearsRefreshCookieWithZeroMaxAgeAndTheSameRestrictedPath() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        cookieService.clear(response);

        assertThat(response.getHeader(HttpHeaders.SET_COOKIE))
                .contains("cardhub_refresh=")
                .contains("Path=/api/v1/auth")
                .contains("Max-Age=0")
                .contains("Secure")
                .contains("HttpOnly")
                .contains("SameSite=Lax");
    }

    @Test
    void readsOnlyTheConfiguredRefreshCookie() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(
                new jakarta.servlet.http.Cookie("another_cookie", "ignored"),
                new jakarta.servlet.http.Cookie("cardhub_refresh", "opaque-refresh-token")
        );

        assertThat(cookieService.readRefreshToken(request)).isEqualTo("opaque-refresh-token");
    }

    @Test
    void returnsNullWhenTheRefreshCookieIsMissing() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        assertThat(cookieService.readRefreshToken(request)).isNull();
    }
}
