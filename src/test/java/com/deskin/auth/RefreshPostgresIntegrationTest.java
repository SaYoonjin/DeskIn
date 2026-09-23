package com.deskin.auth;

import com.deskin.auth.entity.RefreshToken;
import com.deskin.auth.repository.RefreshTokenRepository;
import com.deskin.auth.repository.UserRepository;
import com.deskin.auth.token.OpaqueTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import java.time.Instant;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RefreshPostgresIntegrationTest extends AuthPostgresTestSupport {
    @Autowired RefreshTokenRepository refreshTokens;
    @Autowired UserRepository users;
    @Autowired OpaqueTokenProvider opaqueTokens;

    @Test
    void reusesSameRefreshTokenWithoutRotatingOrExtendingExpiry() throws Exception {
        var response = login(createAccount());
        String raw = objectMapper.readTree(response.getContentAsString()).path("data").path("refreshToken").asText();
        String hash = opaqueTokens.hashToken(raw);
        var expiry = refreshTokens.findById(hash).orElseThrow().getExpiresAt();
        long count = refreshTokens.count();
        for (int attempt = 0; attempt < 2; attempt++) {
            mockMvc.perform(post("/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"refreshToken\":\"" + raw + "\"}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.accessToken").isString())
                    .andExpect(jsonPath("$.data.refreshToken").doesNotExist())
                    .andExpect(header().doesNotExist("Set-Cookie"));
        }
        assertThat(refreshTokens.count()).isEqualTo(count);
        assertThat(refreshTokens.findById(hash).orElseThrow().getExpiresAt()).isEqualTo(expiry);
    }

    @Test
    void rejectsExpiredToken() throws Exception {
        String loginId = createAccount();
        String raw = opaqueTokens.createToken();
        refreshTokens.saveAndFlush(new RefreshToken(opaqueTokens.hashToken(raw),
                users.findByLoginId(loginId).orElseThrow(), Instant.now().minusSeconds(1)));
        mockMvc.perform(post("/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + raw + "\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error.code").value("INVALID_REFRESH_TOKEN"));
    }
}
