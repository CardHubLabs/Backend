package com.kant.cardhub.auth.config;

import com.kant.cardhub.common.error.TraceIdFilter;
import com.kant.cardhub.user.domain.UserStatus;
import com.kant.cardhub.user.infrastructure.UserRepository;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ActiveUserFilterTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final ActiveUserFilter filter = new ActiveUserFilter(userRepository);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void suspendedJwtUserReceivesStandard403AndRequestDoesNotReachController() throws Exception {
        UUID userId = UUID.randomUUID();
        authenticate(userId);
        when(userRepository.findStatusById(userId)).thenReturn(Optional.of(UserStatus.SUSPENDED));
        MockHttpServletRequest request = request("suspended403");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString(StandardCharsets.UTF_8))
                .contains("\"code\":\"USER_SUSPENDED\"")
                .contains("\"traceId\":\"suspended403\"")
                .contains("\"fieldErrors\":[]");
        verifyNoInteractions(chain);
    }

    @Test
    void withdrawnJwtUserReceivesStandard401AndRequestDoesNotReachController() throws Exception {
        UUID userId = UUID.randomUUID();
        authenticate(userId);
        when(userRepository.findStatusById(userId)).thenReturn(Optional.of(UserStatus.WITHDRAWN));
        MockHttpServletRequest request = request("withdrawn401");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString(StandardCharsets.UTF_8))
                .contains("\"code\":\"UNAUTHORIZED\"")
                .contains("\"traceId\":\"withdrawn401\"")
                .contains("\"fieldErrors\":[]");
        verifyNoInteractions(chain);
    }

    private void authenticate(UUID userId) {
        Instant now = Instant.parse("2026-08-25T00:00:00Z");
        Jwt jwt = Jwt.withTokenValue("access-token")
                .header("alg", "RS256")
                .subject(userId.toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(1800))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    private MockHttpServletRequest request(String traceId) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users/me");
        request.setAttribute(TraceIdFilter.TRACE_ID_ATTRIBUTE, traceId);
        return request;
    }
}
