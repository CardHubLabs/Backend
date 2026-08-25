package com.kant.cardhub.auth.config;

import com.kant.cardhub.auth.api.AuthCookieService;
import com.kant.cardhub.auth.application.AuthSessionService;
import com.kant.cardhub.auth.application.AuthTokens;
import com.kant.cardhub.auth.application.OAuthUserProvisioningService;
import com.kant.cardhub.user.domain.User;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class OAuthLoginSuccessHandler implements AuthenticationSuccessHandler {

    private final OAuthUserProvisioningService provisioningService;
    private final AuthSessionService authSessionService;
    private final AuthCookieService authCookieService;
    private final AuthProperties properties;

    public OAuthLoginSuccessHandler(
            OAuthUserProvisioningService provisioningService,
            AuthSessionService authSessionService,
            AuthCookieService authCookieService,
            AuthProperties properties
    ) {
        this.provisioningService = provisioningService;
        this.authSessionService = authSessionService;
        this.authCookieService = authCookieService;
        this.properties = properties;
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException, ServletException {
        OAuth2AuthenticationToken oauth = (OAuth2AuthenticationToken) authentication;
        User user = provisioningService.provision(oauth.getAuthorizedClientRegistrationId(), oauth.getPrincipal());
        AuthTokens tokens = authSessionService.create(
                user,
                request.getHeader("User-Agent"),
                request.getRemoteAddr()
        );
        authCookieService.write(response, tokens.refreshToken());
        if (request.getSession(false) != null) {
            request.getSession(false).invalidate();
        }
        SecurityContextHolder.clearContext();
        response.sendRedirect(properties.oauthSuccessUrl());
    }
}
