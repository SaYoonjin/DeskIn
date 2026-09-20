package com.deskin.auth;

import com.deskin.auth.dto.SignupRequest;
import com.deskin.auth.entity.User;
import com.deskin.auth.entity.UserRole;
import com.deskin.auth.repository.UserRepository;
import com.deskin.auth.service.impl.AuthServiceImpl;
import com.deskin.global.exception.CustomException;
import com.deskin.global.exception.ErrorCode;
import com.deskin.seller.repository.SellerRepository;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SignupServiceTest {
    @Mock UserRepository users;
    @Mock SellerRepository sellers;
    final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);

    private AuthServiceImpl service() {
        return new AuthServiceImpl(users, sellers, encoder);
    }

    private SignupRequest request(UserRole role, String storeName) {
        return new SignupRequest(" Buyer_1 ", "Password123!", " 이름 ", "010-1234-5678",
                role, " User@Example.com ", storeName);
    }

    @Test
    void normalizesAndHashesBuyerWithoutCreatingSeller() {
        when(users.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var result = service().createUser(request(UserRole.BUYER, null));
        assertThat(result.id()).isEqualTo("buyer_1");
        verify(users).save(argThat(user -> user.getName().equals("이름")
                && user.getEmail().equals("user@example.com") && user.getPhone().equals("01012345678")
                && encoder.matches("Password123!", user.getPasswordHash())));
        verifyNoInteractions(sellers);
    }

    @Test
    void createsSellerLinkedToNewUser() {
        when(users.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        service().createUser(request(UserRole.SELLER, " 상점 "));
        verify(sellers).save(argThat(seller -> seller.getStoreName().equals("상점")
                && seller.getUser().getRole() == UserRole.SELLER));
    }

    @Test
    void rejectsDuplicateIdBeforeSaving() {
        when(users.existsByLoginId("buyer_1")).thenReturn(true);
        assertError(() -> service().createUser(request(UserRole.BUYER, null)), ErrorCode.DUPLICATE_LOGIN_ID);
        verify(users, never()).save(any());
    }

    @Test
    void rejectsDuplicateEmailBeforeSaving() {
        when(users.existsByEmail("user@example.com")).thenReturn(true);
        assertError(() -> service().createUser(request(UserRole.BUYER, null)), ErrorCode.DUPLICATE_EMAIL);
        verify(users, never()).save(any());
    }

    @Test
    void rejectsAdminAndInconsistentSellerFields() {
        assertError(() -> service().createUser(request(UserRole.ADMIN, null)), ErrorCode.INVALID_SIGNUP_ROLE);
        assertError(() -> service().createUser(request(UserRole.SELLER, " ")), ErrorCode.VALIDATION_FAILED);
        assertError(() -> service().createUser(request(UserRole.BUYER, "상점")), ErrorCode.VALIDATION_FAILED);
        verifyNoInteractions(users, sellers);
    }

    @Test
    void validatesPasswordByteLimitAndRequiredFields() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertThat(validator.validate(request(UserRole.BUYER, null))).isEmpty();
            for (String password : List.of("short", "한".repeat(25), "a".repeat(65))) {
                var invalid = new SignupRequest("buyer_1", password, "이름", null, UserRole.BUYER, "a@example.com", null);
                assertThat(validator.validate(invalid)).anyMatch(error -> error.getPropertyPath().toString().equals("password"));
            }
            assertThat(validator.validate(new SignupRequest(null, null, null, "abc", null, "bad", null)))
                    .hasSizeGreaterThanOrEqualTo(6);
        }
    }

    private void assertError(Runnable action, ErrorCode expected) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(CustomException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(expected));
    }
}
