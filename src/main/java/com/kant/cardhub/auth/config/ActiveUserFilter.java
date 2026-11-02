package com.kant.cardhub.auth.config;

import com.kant.cardhub.common.error.TraceIdFilter;
import com.kant.cardhub.user.domain.UserStatus;
import com.kant.cardhub.user.infrastructure.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
public class ActiveUserFilter extends OncePerRequestFilter {

    private final UserRepository userRepository;

    public ActiveUserFilter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken)) {
            filterChain.doFilter(request, response);
            return;
        }

        UserStatus status;
        try {
            UUID userId = UUID.fromString(authentication.getName());
            status = userRepository.findStatusById(userId).orElse(UserStatus.WITHDRAWN);
        } catch (IllegalArgumentException exception) {
            status = UserStatus.WITHDRAWN;
        }

        if (status == UserStatus.ACTIVE) {
            if (requiresOnboarding(request)
                    && !userRepository.findOnboardingCompletedById(UUID.fromString(authentication.getName())).orElse(false)) {
                writeError(request, response, 422, "ONBOARDING_REQUIRED", "가입 정보를 완성해야 합니다.");
                return;
            }
            filterChain.doFilter(request, response);
            return;
        }

        SecurityContextHolder.clearContext();
        int httpStatus = status == UserStatus.SUSPENDED ? 403 : 401;
        String code = status == UserStatus.SUSPENDED ? "USER_SUSPENDED" : "UNAUTHORIZED";
        String message = status == UserStatus.SUSPENDED ? "정지된 사용자입니다." : "인증이 필요합니다.";
        writeError(request, response, httpStatus, code, message);
    }

    private boolean requiresOnboarding(HttpServletRequest request) {
        String method = request.getMethod();
        if ("GET".equals(method) || "HEAD".equals(method) || "OPTIONS".equals(method)) {
            return false;
        }
        String path = request.getRequestURI();
        return !path.startsWith("/api/v1/users/me") && !path.startsWith("/api/v1/auth");
    }

    private void writeError(
            HttpServletRequest request,
            HttpServletResponse response,
            int status,
            String code,
            String message
    ) throws IOException {
        Object trace = request.getAttribute(TraceIdFilter.TRACE_ID_ATTRIBUTE);
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":\"" + code + "\",\"message\":\"" + message
                + "\",\"traceId\":\"" + (trace == null ? "unknown" : trace) + "\",\"fieldErrors\":[]}");
    }
}
