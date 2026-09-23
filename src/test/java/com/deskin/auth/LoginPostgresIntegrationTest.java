package com.deskin.auth;

import com.deskin.auth.repository.RefreshTokenRepository;
import com.deskin.auth.token.OpaqueTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class LoginPostgresIntegrationTest extends AuthPostgresTestSupport {
    @Autowired RefreshTokenRepository refreshTokens;
    @Autowired OpaqueTokenProvider opaqueTokens;

    @Test
    void returnsTokensAndPersistsOnlyHashWithExpiryAndCreatedAt() throws Exception {
        var response = login(createAccount());
        String raw = objectMapper.readTree(response.getContentAsString()).path("data").path("refreshToken").asText();
        assertThat(opaqueTokens.isValidFormat(raw)).isTrue();
        var stored = refreshTokens.findById(opaqueTokens.hashToken(raw)).orElseThrow();
        assertThat(stored.getTokenHash()).isNotEqualTo(raw);
        assertThat(stored.getCreatedAt()).isNotNull();
        assertThat(stored.getExpiresAt()).isAfter(stored.getCreatedAt());
        assertThat(response.getHeader("Set-Cookie")).isNull();
        assertThat(accessToken(response)).isNotBlank();
    }

    @Test
    void returnsSameErrorForUnknownIdAndWrongPassword() throws Exception {
        String loginId = createAccount();
        for (String id : new String[]{loginId, "unknown_user"}) {
            mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"id\":\"" + id + "\",\"password\":\"WrongPassword\"}"))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"))
                    .andExpect(header().doesNotExist("Set-Cookie"));
        }
    }
}
