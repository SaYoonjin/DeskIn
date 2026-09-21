package com.deskin.auth;

import com.deskin.auth.dto.LoginRequest;
import com.deskin.auth.entity.*;
import com.deskin.global.exception.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class LoginServiceTest extends AuthServiceTestSupport {
    @Test
    void storesOnlyHashedRefreshTokensLinkedToUser() {
        when(users.findByLoginId("buyer_1")).thenReturn(Optional.of(createUser(UserRole.BUYER)));
        var first = service.authenticateUser(new LoginRequest(" BUYER_1 ", "Password123!"));
        var second = service.authenticateUser(new LoginRequest("buyer_1", "Password123!"));
        var firstPrincipal = jwtTokens.parseAccessToken(first.accessToken());
        var secondPrincipal = jwtTokens.parseAccessToken(second.accessToken());
        assertThat(firstPrincipal.userId()).isEqualTo(12L);
        assertThat(firstPrincipal.role()).isEqualTo(UserRole.BUYER);
        var captured = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokens, times(2)).save(captured.capture());
        assertThat(captured.getAllValues().get(0).getTokenHash()).isEqualTo(opaqueTokens.hashToken(first.refreshToken()))
                .isNotEqualTo(first.refreshToken());
        assertThat(first.refreshToken()).isNotEqualTo(second.refreshToken());
        assertThat(captured.getValue().getUser().getUserId()).isEqualTo(12L);
        assertThat(captured.getValue().getExpiresAt()).isEqualTo(clock.instant().plus(properties.refreshTokenDuration()));
    }

    @Test
    void rejectsWrongPasswordWithoutIssuingTokens() {
        when(users.findByLoginId("buyer_1")).thenReturn(Optional.of(createUser(UserRole.BUYER)));
        assertCredentialsFailure(new LoginRequest("buyer_1", "incorrect"));
        verifyNoInteractions(refreshTokens);
    }

    @Test
    void rejectsUnknownUserWithSameError() {
        when(users.findByLoginId("unknown")).thenReturn(Optional.empty());
        assertCredentialsFailure(new LoginRequest("unknown", "Password123!"));
        verify(encoder).matches(eq("Password123!"), anyString());
        verifyNoInteractions(refreshTokens);
    }

    private void assertCredentialsFailure(LoginRequest request) {
        assertThatThrownBy(() -> service.authenticateUser(request)).isInstanceOfSatisfying(CustomException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_CREDENTIALS));
    }
}
