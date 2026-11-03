package com.kant.cardhub.auth.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
class OAuthLoginFailureHandler implements AuthenticationFailureHandler {

    private final OAuthLoginRedirect redirect;

    OAuthLoginFailureHandler(OAuthLoginRedirect redirect) {
        this.redirect = redirect;
    }

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception
    ) throws IOException {
        boolean accessDenied = exception instanceof OAuth2AuthenticationException oauth
                && OAuth2ErrorCodes.ACCESS_DENIED.equals(oauth.getError().getErrorCode());
        redirect.failure(request, response, accessDenied
                ? OAuthLoginRedirect.Error.ACCESS_DENIED
                : OAuthLoginRedirect.Error.OAUTH_FAILED);
    }
}
