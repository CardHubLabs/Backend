package com.kant.cardhub.auth.config;

import com.kant.cardhub.auth.api.AuthCookieService;
import com.kant.cardhub.auth.application.AuthSessionService;
import com.kant.cardhub.auth.application.AuthTokens;
import com.kant.cardhub.auth.application.OAuthUserProvisioningService;
import com.kant.cardhub.common.error.BusinessException;
import com.kant.cardhub.common.error.ErrorCode;
import com.kant.cardhub.user.domain.User;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class OAuthLoginSuccessHandler implements AuthenticationSuccessHandler {

    private final OAuthUserProvisioningService provisioningService;
    private final AuthSessionService authSessionService;
    private final AuthCookieService authCookieService;
    private final OAuthLoginRedirect redirect;

    public OAuthLoginSuccessHandler(
            OAuthUserProvisioningService provisioningService,
            AuthSessionService authSessionService,
            AuthCookieService authCookieService,
            OAuthLoginRedirect redirect
    ) {
        this.provisioningService = provisioningService;
        this.authSessionService = authSessionService;
        this.authCookieService = authCookieService;
        this.redirect = redirect;
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException, ServletException {
        OAuth2AuthenticationToken oauth = (OAuth2AuthenticationToken) authentication;
        AuthTokens tokens;
        try {
            User user = provisioningService.provision(oauth.getAuthorizedClientRegistrationId(), oauth.getPrincipal());
            tokens = authSessionService.create(user, request.getHeader("User-Agent"), request.getRemoteAddr());
        } catch (BusinessException exception) {
            OAuthLoginRedirect.Error error = exception.errorCode() == ErrorCode.USER_SUSPENDED
                    || exception.errorCode() == ErrorCode.UNAUTHORIZED
                    || exception.errorCode() == ErrorCode.USER_NOT_FOUND
                    ? OAuthLoginRedirect.Error.ACCOUNT_UNAVAILABLE
                    : OAuthLoginRedirect.Error.OAUTH_FAILED;
            redirect.failure(request, response, error);
            return;
        } catch (RuntimeException exception) {
            redirect.failure(request, response, OAuthLoginRedirect.Error.OAUTH_FAILED);
            return;
        }
        authCookieService.write(response, tokens.refreshToken());
        redirect.success(request, response);
    }
}
