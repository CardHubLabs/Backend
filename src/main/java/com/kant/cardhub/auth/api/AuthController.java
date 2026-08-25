package com.kant.cardhub.auth.api;

import com.kant.cardhub.auth.application.AuthSessionService;
import com.kant.cardhub.auth.application.AuthTokens;
import com.kant.cardhub.auth.config.AuthProperties;
import com.kant.cardhub.common.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthSessionService authSessionService;
    private final AuthCookieService authCookieService;
    private final AuthProperties properties;

    public AuthController(
            AuthSessionService authSessionService,
            AuthCookieService authCookieService,
            AuthProperties properties
    ) {
        this.authSessionService = authSessionService;
        this.authCookieService = authCookieService;
        this.properties = properties;
    }

    @GetMapping("/csrf")
    ApiResponse<Map<String, String>> csrf(CsrfToken csrfToken) {
        return ApiResponse.success(Map.of(
                "headerName", csrfToken.getHeaderName(),
                "parameterName", csrfToken.getParameterName(),
                "token", csrfToken.getToken()
        ));
    }

    @PostMapping("/reissue")
    ApiResponse<TokenResponse> reissue(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        AuthTokens tokens = authSessionService.reissue(
                refreshToken(request),
                request.getHeader("User-Agent"),
                request.getRemoteAddr()
        );
        authCookieService.write(response, tokens.refreshToken());
        return ApiResponse.success(TokenResponse.from(tokens));
    }

    @PostMapping("/logout")
    ResponseEntity<ApiResponse<Void>> logout(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        authSessionService.logout(refreshToken(request));
        authCookieService.clear(response);
        return ResponseEntity.ok(ApiResponse.empty());
    }

    private String refreshToken(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }
        for (jakarta.servlet.http.Cookie cookie : request.getCookies()) {
            if (properties.refreshCookieName().equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
