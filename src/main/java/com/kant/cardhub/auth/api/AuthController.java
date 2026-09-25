package com.kant.cardhub.auth.api;

import com.kant.cardhub.auth.application.AuthSessionService;
import com.kant.cardhub.auth.application.AuthTokens;
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

    public AuthController(
            AuthSessionService authSessionService,
            AuthCookieService authCookieService
    ) {
        this.authSessionService = authSessionService;
        this.authCookieService = authCookieService;
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
                authCookieService.readRefreshToken(request),
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
        authSessionService.logout(authCookieService.readRefreshToken(request));
        authCookieService.clear(response);
        return ResponseEntity.ok(ApiResponse.empty());
    }
}
