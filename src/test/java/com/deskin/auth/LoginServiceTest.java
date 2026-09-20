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
    void createsSeparateSessionsAndStoresOnlyHashedRefreshTokens() {
        when(users.findByLoginId("buyer_1")).thenReturn(Optional.of(createUser(UserRole.BUYER)));
        when(sessions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var first = service.authenticateUser(new LoginRequest(" BUYER_1 ", "Password123!"));
        var second = service.authenticateUser(new LoginRequest("buyer_1", "Password123!"));
        var firstPrincipal = jwtTokens.parseAccessToken(first.response().accessToken());
        var secondPrincipal = jwtTokens.parseAccessToken(second.response().accessToken());
        assertThat(firstPrincipal.userId()).isEqualTo(12L);
        assertThat(firstPrincipal.role()).isEqualTo(UserRole.BUYER);
        assertThat(firstPrincipal.sessionId()).isNotEqualTo(secondPrincipal.sessionId());
        assertThat(first.expiresAt()).isEqualTo(clock.instant().plus(properties.sessionDuration()));
        var captured = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokens, times(2)).save(captured.capture());
        assertThat(captured.getAllValues().get(0).getTokenHash()).isEqualTo(opaqueTokens.hashToken(first.refreshToken()))
                .isNotEqualTo(first.refreshToken());
        assertThat(first.refreshToken()).isNotEqualTo(second.refreshToken());
    }

    @Test
    void rejectsWrongPasswordWithoutCreatingSession() {
        when(users.findByLoginId("buyer_1")).thenReturn(Optional.of(createUser(UserRole.BUYER)));
        assertCredentialsFailure(new LoginRequest("buyer_1", "incorrect"));
        verifyNoInteractions(sessions, refreshTokens);
    }

    @Test
    void rejectsUnknownUserWithSameError() {
        when(users.findByLoginId("unknown")).thenReturn(Optional.empty());
        assertCredentialsFailure(new LoginRequest("unknown", "Password123!"));
        verifyNoInteractions(sessions, refreshTokens);
    }

    private void assertCredentialsFailure(LoginRequest request) {
        assertThatThrownBy(() -> service.authenticateUser(request)).isInstanceOfSatisfying(CustomException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_CREDENTIALS));
    }
}
