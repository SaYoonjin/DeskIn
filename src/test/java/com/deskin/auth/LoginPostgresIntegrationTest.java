package com.deskin.auth;

import com.deskin.auth.repository.*;
import com.deskin.auth.token.JwtTokenProvider;
import com.deskin.auth.token.OpaqueTokenProvider;
import com.deskin.auth.security.RefreshCookieWriter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class LoginPostgresIntegrationTest extends AuthPostgresTestSupport {
    @Autowired LoginSessionRepository sessions;
    @Autowired RefreshTokenRepository refreshTokens;
    @Autowired JwtTokenProvider jwtTokens;
    @Autowired OpaqueTokenProvider opaqueTokens;

    @Test
    void persistsSeparateSessionsAndReturnsOnlyAccessTokenInBody() throws Exception {
        String loginId = createAccount();
        var first = login(loginId);
        var second = login(loginId);
        var firstPrincipal = jwtTokens.parseAccessToken(accessToken(first));
        var secondPrincipal = jwtTokens.parseAccessToken(accessToken(second));
        assertThat(firstPrincipal.sessionId()).isNotEqualTo(secondPrincipal.sessionId());
        assertThat(sessions.findById(firstPrincipal.sessionId())).isPresent();
        assertThat(sessions.findById(secondPrincipal.sessionId())).isPresent();
        var cookie = first.getCookie(RefreshCookieWriter.COOKIE_NAME);
        assertThat(cookie).isNotNull();
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getPath()).isEqualTo("/auth");
        assertThat(refreshTokens.findById(opaqueTokens.hashToken(cookie.getValue()))).isPresent();
        assertThat(first.getContentAsString()).doesNotContain(cookie.getValue(), "refreshToken", "password");
        assertThat(objectMapper.readTree(first.getContentAsString()).path("data").path("role").asText()).isEqualTo("BUYER");
    }

    @Test
    void returnsSameErrorForUnknownIdAndWrongPassword() throws Exception {
        String loginId = createAccount();
        for (String id : new String[]{loginId, "unknown_user"}) {
            mockMvc.perform(post("/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"id\":\"" + id + "\",\"password\":\"WrongPassword\"}"))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"))
                    .andExpect(cookie().doesNotExist(RefreshCookieWriter.COOKIE_NAME));
        }
    }
}
