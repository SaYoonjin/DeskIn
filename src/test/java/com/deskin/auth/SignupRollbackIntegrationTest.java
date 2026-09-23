package com.deskin.auth;

import com.deskin.support.PostgresIntegrationTest;

import com.deskin.auth.dto.SignupRequest;
import com.deskin.auth.entity.UserRole;
import com.deskin.auth.repository.UserRepository;
import com.deskin.auth.service.AuthService;
import com.deskin.seller.entity.Seller;
import com.deskin.seller.repository.SellerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
@ActiveProfiles("test")
class SignupRollbackIntegrationTest extends PostgresIntegrationTest {
    @Autowired AuthService authService;
    @Autowired UserRepository userRepository;
    @SpyBean SellerRepository sellerRepository;

    @Test
    void rollsBackUserWhenSellerCreationFails() {
        doThrow(new IllegalStateException("테스트용 판매자 저장 실패")).when(sellerRepository).save(any(Seller.class));
        var request = new SignupRequest("rollback_seller", "Password123!", "판매자", "01012345678",
                UserRole.SELLER, "rollback@example.com");

        assertThatThrownBy(() -> authService.createUser(request)).isInstanceOf(IllegalStateException.class);
        assertThat(userRepository.findByLoginId("rollback_seller")).isEmpty();
    }
}
