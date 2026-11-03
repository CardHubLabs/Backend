package com.kant.cardhub.auth.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

@Component
class OAuthLoginRedirect {

    enum Error {
        ACCESS_DENIED("access_denied"),
        ACCOUNT_UNAVAILABLE("account_unavailable"),
        OAUTH_FAILED("oauth_failed");

        private final String code;

        Error(String code) {
            this.code = code;
        }
    }

    private final AuthProperties properties;

    OAuthLoginRedirect(AuthProperties properties) {
        this.properties = properties;
    }

    void success(HttpServletRequest request, HttpServletResponse response) throws IOException {
        clearTemporaryAuthentication(request);
        response.sendRedirect(properties.oauthSuccessUrl());
    }

    void failure(HttpServletRequest request, HttpServletResponse response, Error error) throws IOException {
        clearTemporaryAuthentication(request);
        String url = UriComponentsBuilder.fromUriString(properties.oauthSuccessUrl())
                .replaceQueryParam("error", error.code)
                .build()
                .encode()
                .toUriString();
        response.sendRedirect(url);
    }

    private void clearTemporaryAuthentication(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
    }
}
