package com.deskin.auth;

import com.deskin.support.PostgresIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import java.util.Map;
import java.util.UUID;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
abstract class AuthPostgresTestSupport extends PostgresIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    String createAccount() throws Exception {
        String loginId = "u_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        mockMvc.perform(post("/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("id", loginId, "password", "Password123!",
                                "name", "이름", "phone", "010-1234-5678", "role", "BUYER", "email", loginId + "@example.com"))))
                .andExpect(status().isCreated());
        return loginId;
    }

    MockHttpServletResponse login(String loginId) throws Exception {
        return mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("id", loginId, "password", "Password123!"))))
                .andExpect(status().isOk()).andReturn().getResponse();
    }

    String accessToken(MockHttpServletResponse response) throws Exception {
        return objectMapper.readTree(response.getContentAsString()).path("data").path("accessToken").asText();
    }
}
