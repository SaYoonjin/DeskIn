package com.deskin.auth;

import com.deskin.auth.entity.*;
import com.deskin.auth.repository.UserRepository;
import com.deskin.auth.security.AuthPrincipal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Import(AuthorizationPostgresIntegrationTest.Endpoints.class)
class AuthorizationPostgresIntegrationTest extends AuthPostgresTestSupport {
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwords;

    @RestController
    static class Endpoints {
        @GetMapping({"/orders/probe", "/payments/probe", "/seller/probe", "/admin/probe"})
        Long getUserId(@AuthenticationPrincipal AuthPrincipal principal) { return principal.userId(); }

        @GetMapping({"/products", "/products/{productId}"})
        String getProducts() { return "ok"; }
    }

    @ParameterizedTest
    @EnumSource(UserRole.class)
    void authorizesPersistedSessionsWithoutRoleInheritance(UserRole role) throws Exception {
        String loginId = "role_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        // 관리자 계정은 공개 가입이 아닌 테스트용 저장소 픽스처로만 생성한다.
        User user = users.save(new User(loginId, passwords.encode("Password123!"), "이름", null,
                loginId + "@example.com", role));
        String token = accessToken(login(loginId));
        for (String prefix : new String[]{"/orders", "/payments", "/seller", "/admin"}) {
            UserRole required = switch (prefix) {
                case "/orders", "/payments" -> UserRole.BUYER;
                case "/seller" -> UserRole.SELLER;
                default -> UserRole.ADMIN;
            };
            var result = mockMvc.perform(get(prefix + "/probe").header("Authorization", "Bearer " + token));
            if (role == required) {
                result.andExpect(status().isOk()).andExpect(content().string(user.getUserId().toString()));
            } else {
                result.andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("ACCESS_DENIED"));
            }
        }
    }

    @Test
    void exposesOnlyPublicProductsToAnonymousUsers() throws Exception {
        mockMvc.perform(get("/products")).andExpect(status().isOk());
        mockMvc.perform(get("/products/1")).andExpect(status().isOk());
        mockMvc.perform(get("/orders/probe")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/webhooks/toss")).andExpect(status().isUnauthorized());
    }
}
