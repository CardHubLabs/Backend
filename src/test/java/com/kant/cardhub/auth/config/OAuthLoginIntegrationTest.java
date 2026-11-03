package com.kant.cardhub.auth.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "GOOGLE_CLIENT_ID=test-google", "GOOGLE_CLIENT_SECRET=test-google-secret",
        "NAVER_CLIENT_ID=test-naver", "NAVER_CLIENT_SECRET=test-naver-secret",
        "KAKAO_CLIENT_ID=test-kakao", "KAKAO_CLIENT_SECRET=test-kakao-secret"
})
@AutoConfigureMockMvc
@ActiveProfiles({"test", "oauth"})
class OAuthLoginIntegrationTest {

    @Autowired MockMvc mockMvc;

    @Test
    void kakaoAuthorizationRequestsOnlyNicknameAndCancellationRedirectsSafely() throws Exception {
        MvcResult start = mockMvc.perform(get("/oauth2/authorization/kakao"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        URI target = URI.create(start.getResponse().getRedirectedUrl());
        var query = UriComponentsBuilder.fromUri(target).build().getQueryParams();
        assertThat(target.getHost()).isEqualTo("kauth.kakao.com");
        assertThat(query.getFirst("scope")).isEqualTo("profile_nickname");
        assertThat(query.getFirst("redirect_uri")).isEqualTo("http://localhost/login/oauth2/code/kakao");
        String state = UriUtils.decode(query.getFirst("state"), StandardCharsets.UTF_8);
        assertThat(state).isNotBlank();
        MockHttpSession session = (MockHttpSession) start.getRequest().getSession(false);
        assertThat(session).isNotNull();

        MvcResult result = mockMvc.perform(get("/login/oauth2/code/kakao")
                        .session(session)
                        .param("state", state)
                        .param("error", "access_denied")
                        .param("error_description", "provider-secret-description"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost:3000/auth/callback?error=access_denied"))
                .andReturn();
        assertThat(result.getResponse().getHeaders("Set-Cookie"))
                .noneMatch(cookie -> cookie.startsWith("cardhub_refresh="));
    }

    @Test
    void badCallbackUsesGenericErrorInsteadOfDefaultLoginPage() throws Exception {
        mockMvc.perform(get("/login/oauth2/code/kakao").param("code", "invalid"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost:3000/auth/callback?error=oauth_failed"));
    }
}
