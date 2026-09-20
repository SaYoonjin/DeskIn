package com.deskin.auth;

import com.deskin.auth.repository.LoginSessionRepository;
import com.deskin.auth.service.AuthService;
import com.deskin.global.auth.*;
import com.deskin.global.config.JwtProperties;
import com.deskin.global.exception.CustomException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.List;
import java.util.concurrent.*;
import java.time.Clock;
import java.time.Duration;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class LogoutPostgresIntegrationTest extends AuthPostgresTestSupport {
    @Autowired LoginSessionRepository sessions;
    @Autowired JwtTokenProvider jwtTokens;
    @Autowired AuthService authService;
    @Autowired JwtProperties jwtProperties;

    @Test
    void immediatelyRejectsBothTokensButKeepsOtherDeviceLoggedIn() throws Exception {
        String loginId = createAccount();
        var first = login(loginId);
        var second = login(loginId);
        var response = mockMvc.perform(post("/auth/logout").with(csrf())
                        .header("Authorization", "Bearer " + accessToken(first)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.message").value("로그아웃 되었습니다."))
                .andExpect(jsonPath("$.data").doesNotExist()).andReturn().getResponse();
        assertThat(response.getCookie(RefreshCookieWriter.COOKIE_NAME).getMaxAge()).isZero();
        assertThat(response.getCookie(RefreshCookieWriter.COOKIE_NAME).getPath()).isEqualTo("/auth");
        assertThat(sessions.findById(jwtTokens.parseAccessToken(accessToken(first)).sessionId()).orElseThrow().getRevokedAt()).isNotNull();

        mockMvc.perform(post("/auth/logout").with(csrf()).header("Authorization", "Bearer " + accessToken(first)))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error.code").value("SESSION_REVOKED"));
        mockMvc.perform(post("/auth/refresh").with(csrf()).cookie(first.getCookie(RefreshCookieWriter.COOKIE_NAME)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/auth/refresh").with(csrf()).cookie(second.getCookie(RefreshCookieWriter.COOKIE_NAME)))
                .andExpect(status().isOk());
    }

    @Test
    void requiresBearerTokenAndCsrf() throws Exception {
        mockMvc.perform(post("/auth/logout").with(csrf()))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error.code").value("INVALID_ACCESS_TOKEN"));
        var login = login(createAccount());
        mockMvc.perform(post("/auth/logout").header("Authorization", "Bearer " + accessToken(login)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("CSRF_VALIDATION_FAILED"));
    }

    @Test
    void refreshesExpiredAccessTokenBeforeLogout() throws Exception {
        var login = login(createAccount());
        var principal = jwtTokens.parseAccessToken(accessToken(login));
        var session = sessions.findById(principal.sessionId()).orElseThrow();
        String expired = new JwtTokenProvider(jwtProperties, Clock.offset(Clock.systemUTC(), Duration.ofHours(-1)))
                .createAccessToken(session);
        mockMvc.perform(post("/auth/logout").with(csrf()).header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error.code").value("ACCESS_TOKEN_EXPIRED"));
        var renewed = mockMvc.perform(post("/auth/refresh").with(csrf())
                        .cookie(login.getCookie(RefreshCookieWriter.COOKIE_NAME)))
                .andExpect(status().isOk()).andReturn().getResponse();
        mockMvc.perform(post("/auth/logout").with(csrf()).header("Authorization", "Bearer " + accessToken(renewed)))
                .andExpect(status().isOk());
    }

    @Test
    void concurrentLogoutAndRefreshAlwaysLeaveSessionRevoked() throws Exception {
        var login = login(createAccount());
        var principal = jwtTokens.parseAccessToken(accessToken(login));
        String refreshToken = login.getCookie(RefreshCookieWriter.COOKIE_NAME).getValue();
        var start = new CyclicBarrier(2);
        var executor = Executors.newFixedThreadPool(2);
        Callable<String> logout = () -> {
            start.await(10, TimeUnit.SECONDS);
            authService.revokeSession(principal);
            return "LOGGED_OUT";
        };
        Callable<String> refresh = () -> {
            start.await(10, TimeUnit.SECONDS);
            try {
                authService.refreshTokens(refreshToken);
                return "REFRESHED";
            } catch (CustomException exception) {
                return exception.getErrorCode().name();
            }
        };
        try {
            var results = executor.invokeAll(List.of(logout, refresh), 30, TimeUnit.SECONDS);
            assertThat(results.get(0).get()).isEqualTo("LOGGED_OUT");
            assertThat(results.get(1).get()).isIn("REFRESHED", "INVALID_REFRESH_TOKEN");
            assertThat(sessions.findById(principal.sessionId()).orElseThrow().getRevokedAt()).isNotNull();
        } finally {
            executor.shutdownNow();
        }
    }
}
