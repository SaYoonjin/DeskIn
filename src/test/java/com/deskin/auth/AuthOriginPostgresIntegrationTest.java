package com.deskin.auth;

import com.deskin.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
class AuthOriginPostgresIntegrationTest extends PostgresIntegrationTest {
    @Autowired MockMvc mockMvc;

    @Test
    void acceptsTrustedOriginWithoutCsrfTokenAndRejectsMissingOrigin() throws Exception {
        mockMvc.perform(post("/auth/signup").header("Origin", "http://localhost:3000")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
        mockMvc.perform(post("/auth/signup").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST_ORIGIN"));
    }
}
