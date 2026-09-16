package com.kant.cardhub.catalog.api;

import com.kant.cardhub.catalog.application.CatalogService;
import com.kant.cardhub.catalog.application.CatalogView;
import com.kant.cardhub.catalog.domain.GameCode;
import com.kant.cardhub.common.security.CurrentUserIdArgumentResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CatalogControllerAuditContractTest {

    private CatalogService catalogService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        catalogService = mock(CatalogService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new CatalogController(catalogService))
                .setCustomArgumentResolvers(new CurrentUserIdArgumentResolver())
                .build();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticatedSubjectIsPassedToCatalogMutationAsAdminId() throws Exception {
        UUID adminId = UUID.randomUUID();
        UUID gameId = UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(adminId.toString(), "n/a", List.of())
        );
        when(catalogService.createGame(adminId, "One Piece", "one-piece", GameCode.ONE_PIECE))
                .thenReturn(new CatalogView.Game(gameId, "One Piece", "one-piece", GameCode.ONE_PIECE));

        mockMvc.perform(post("/api/v1/admin/catalog/games")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "One Piece",
                                  "slug": "one-piece",
                                  "code": "ONE_PIECE"
                                }
                                """))
                .andExpect(status().isOk());

        verify(catalogService).createGame(adminId, "One Piece", "one-piece", GameCode.ONE_PIECE);
    }
}
