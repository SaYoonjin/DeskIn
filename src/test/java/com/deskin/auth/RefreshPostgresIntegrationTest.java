package com.deskin.auth;

import com.deskin.auth.repository.*;
import com.deskin.auth.service.AuthService;
import com.deskin.global.auth.*;
import com.deskin.global.exception.CustomException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.List;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RefreshPostgresIntegrationTest extends AuthPostgresTestSupport {
    @Autowired LoginSessionRepository sessions;
    @Autowired RefreshTokenRepository refreshTokens;
    @Autowired JwtTokenProvider jwtTokens;
    @Autowired OpaqueTokenProvider opaqueTokens;
    @Autowired AuthService authService;

    @Test
    void rotatesAndCommitsRevocationAfterReuseError() throws Exception {
        var login = login(createAccount());
        var originalCookie = login.getCookie(RefreshCookieWriter.COOKIE_NAME);
        var principal = jwtTokens.parseAccessToken(accessToken(login));
        var initialExpiry = sessions.findById(principal.sessionId()).orElseThrow().getExpiresAt();
        var renewed = mockMvc.perform(post("/auth/refresh").with(csrf()).cookie(originalCookie)
                        .header("Authorization", "Bearer expired-token"))
                .andExpect(status().isOk()).andReturn().getResponse();
        assertThat(renewed.getCookie(RefreshCookieWriter.COOKIE_NAME).getValue()).isNotEqualTo(originalCookie.getValue());
        assertThat(refreshTokens.findById(opaqueTokens.hashToken(originalCookie.getValue())).orElseThrow().getUsedAt()).isNotNull();
        assertThat(sessions.findById(principal.sessionId()).orElseThrow().getExpiresAt()).isEqualTo(initialExpiry);

        mockMvc.perform(post("/auth/refresh").with(csrf()).cookie(originalCookie))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error.code").value("REFRESH_TOKEN_REUSED"));
        assertThat(sessions.findById(principal.sessionId()).orElseThrow().getRevokedAt()).isNotNull();
        mockMvc.perform(post("/auth/refresh").with(csrf()).cookie(renewed.getCookie(RefreshCookieWriter.COOKIE_NAME)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void concurrentRefreshIssuesOnlyOneReplacementAndRevokesOnReplay() throws Exception {
        var login = login(createAccount());
        String original = login.getCookie(RefreshCookieWriter.COOKIE_NAME).getValue();
        var principal = jwtTokens.parseAccessToken(accessToken(login));
        var executor = Executors.newFixedThreadPool(2);
        var start = new CyclicBarrier(2);
        Callable<String> request = () -> {
            start.await(10, TimeUnit.SECONDS);
            try {
                authService.refreshTokens(original);
                return "SUCCESS";
            } catch (CustomException exception) {
                return exception.getErrorCode().name();
            }
        };
        try {
            var results = executor.invokeAll(List.of(request, request), 30, TimeUnit.SECONDS);
            assertThat(List.of(results.get(0).get(), results.get(1).get()))
                    .containsExactlyInAnyOrder("SUCCESS", "REFRESH_TOKEN_REUSED");
            assertThat(sessions.findById(principal.sessionId()).orElseThrow().getRevokedAt()).isNotNull();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void rejectsMissingCookieAndCsrf() throws Exception {
        mockMvc.perform(post("/auth/refresh").with(csrf()))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error.code").value("INVALID_REFRESH_TOKEN"));
        var login = login(createAccount());
        mockMvc.perform(post("/auth/refresh").cookie(login.getCookie(RefreshCookieWriter.COOKIE_NAME)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("CSRF_VALIDATION_FAILED"));
    }
}
