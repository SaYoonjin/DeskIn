package com.deskin.auth;

import com.deskin.support.PostgresIntegrationTest;

import com.deskin.auth.repository.UserRepository;
import com.deskin.seller.repository.SellerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SignupIntegrationTest extends PostgresIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired SellerRepository sellerRepository;
    @Autowired PasswordEncoder passwordEncoder;

    @Test
    void createsNormalizedBuyerWithoutSeller() throws Exception {
        mockMvc.perform(post("/auth/signup").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("""
                {"id":" Buyer_1 ","password":"Password123!","name":" 홍길동 ",
                 "role":"BUYER","email":" Buyer@Example.com ","phone":"010-1234-5678"}
                """))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value("buyer_1"))
                .andExpect(jsonPath("$.data.role").value("BUYER"));
        var user = userRepository.findByLoginId("buyer_1").orElseThrow();
        assertThat(user.getName()).isEqualTo("홍길동");
        assertThat(user.getEmail()).isEqualTo("buyer@example.com");
        assertThat(user.getPhone()).isEqualTo("01012345678");
        assertThat(passwordEncoder.matches("Password123!", user.getPasswordHash())).isTrue();
        assertThat(sellerRepository.findByUserUserId(user.getUserId())).isEmpty();
    }

    @Test
    void createsSellerAndAllowsMissingPhone() throws Exception {
        mockMvc.perform(post("/auth/signup").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("""
                {"id":"seller_1","password":"Password123!","name":"판매자",
                 "role":"SELLER","email":"seller@example.com","storeName":" 데스크인 "}
                """))
                .andExpect(status().isCreated());
        var user = userRepository.findByLoginId("seller_1").orElseThrow();
        assertThat(user.getPhone()).isNull();
        assertThat(sellerRepository.findByUserUserId(user.getUserId()).orElseThrow().getStoreName()).isEqualTo("데스크인");
    }

    @Test
    void rejectsInvalidRoleAndSellerFields() throws Exception {
        for (String fields : new String[]{"\"role\":\"ADMIN\"", "\"role\":\"SELLER\"",
                "\"role\":\"BUYER\",\"storeName\":\"상점\""}) {
            mockMvc.perform(post("/auth/signup").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"id\":\"user_1\",\"password\":\"Password123!\",\"name\":\"이름\",\"email\":\"a@example.com\"," + fields + "}"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false));
        }
        assertThat(userRepository.findByLoginId("user_1")).isEmpty();
    }

    @Test
    void rejectsDuplicateIdAndEmail() throws Exception {
        String request = """
                {"id":"unique_1","password":"Password123!","name":"이름","role":"BUYER","email":"unique@example.com"}
                """;
        mockMvc.perform(post("/auth/signup").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/auth/signup").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("DUPLICATE_LOGIN_ID"));
        mockMvc.perform(post("/auth/signup").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(request.replace("unique_1", "unique_2")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("DUPLICATE_EMAIL"));
    }

    @Test
    void rejectsPasswordOverByteLimitAndInvalidJson() throws Exception {
        mockMvc.perform(post("/auth/signup").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("""
                {"id":"user_1","password":"%s","name":"이름","role":"BUYER","email":"a@example.com"}
                """.formatted("한".repeat(25))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fieldErrors[0].field").value("password"));
        mockMvc.perform(post("/auth/signup").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    @Test
    void rejectsMissingCsrf() throws Exception {
        mockMvc.perform(post("/auth/signup").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("CSRF_VALIDATION_FAILED"));
    }
}
