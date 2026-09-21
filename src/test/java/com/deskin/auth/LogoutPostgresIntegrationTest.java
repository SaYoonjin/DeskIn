package com.deskin.auth;

import com.deskin.auth.repository.RefreshTokenRepository;
import com.deskin.auth.token.OpaqueTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Import(LogoutPostgresIntegrationTest.Endpoints.class)
class LogoutPostgresIntegrationTest extends AuthPostgresTestSupport {
    @RestController
    static class Endpoints {
        @GetMapping("/orders/logout-probe")
        String getOrder() { return "ok"; }
    }

    @Autowired RefreshTokenRepository refreshTokens;
    @Autowired OpaqueTokenProvider opaqueTokens;

    @Test
    void deletesOnlyGivenRefreshTokenWhileAccessTokenRemainsValid() throws Exception {
        String loginId = createAccount();
        var first = login(loginId);
        var second = login(loginId);
        String raw = objectMapper.readTree(first.getContentAsString()).path("data").path("refreshToken").asText();
        String other = objectMapper.readTree(second.getContentAsString()).path("data").path("refreshToken").asText();
        String body = "{\"refreshToken\":\"" + raw + "\"}";
        for (int attempt = 0; attempt < 2; attempt++) {
            mockMvc.perform(post("/auth/logout").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isOk()).andExpect(header().doesNotExist("Set-Cookie"));
        }
        assertThat(refreshTokens.findById(opaqueTokens.hashToken(raw))).isEmpty();
        assertThat(refreshTokens.findById(opaqueTokens.hashToken(other))).isPresent();
        mockMvc.perform(post("/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/orders/logout-probe").header("Authorization", "Bearer " + accessToken(first)))
                .andExpect(status().isOk());
    }
}
