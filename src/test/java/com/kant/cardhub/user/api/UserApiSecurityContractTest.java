package com.kant.cardhub.user.api;

import com.kant.cardhub.common.error.GlobalExceptionHandler;
import com.kant.cardhub.common.error.TraceIdFilter;
import com.kant.cardhub.common.security.CurrentUserIdArgumentResolver;
import com.kant.cardhub.user.application.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserApiSecurityContractTest {

    private UserService userService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        userService = mock(UserService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new UserController(userService))
                .setCustomArgumentResolvers(new CurrentUserIdArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new TraceIdFilter())
                .build();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void unauthenticatedCurrentUserRequestReturnsStandard401BodyAndTraceId() throws Exception {
        mockMvc.perform(get("/api/v1/users/me")
                        .header(TraceIdFilter.TRACE_ID_HEADER, "authtrace401"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(TraceIdFilter.TRACE_ID_HEADER, "authtrace401"))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("인증이 필요합니다."))
                .andExpect(jsonPath("$.traceId").value("authtrace401"))
                .andExpect(jsonPath("$.fieldErrors").isArray());
    }

    @Test
    void ownershipViolationReturnsStandard403BodyAndTraceId() throws Exception {
        UUID userId = UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId.toString(), "n/a", List.of())
        );
        when(userService.get(userId)).thenThrow(new SecurityException("another user's resource"));

        mockMvc.perform(get("/api/v1/users/me")
                        .header(TraceIdFilter.TRACE_ID_HEADER, "authtrace403"))
                .andExpect(status().isForbidden())
                .andExpect(header().string(TraceIdFilter.TRACE_ID_HEADER, "authtrace403"))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("접근 권한이 없습니다."))
                .andExpect(jsonPath("$.traceId").value("authtrace403"))
                .andExpect(jsonPath("$.fieldErrors").isArray());
    }

    @Test
    void malformedAuthenticatedSubjectIsTreatedAsUnauthorized() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("not-a-uuid", "n/a", List.of())
        );

        mockMvc.perform(get("/api/v1/users/me")
                        .header(TraceIdFilter.TRACE_ID_HEADER, "badsubject01"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.traceId").value("badsubject01"));
    }
}
