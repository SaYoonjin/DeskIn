package com.deskin.auth;

import com.deskin.auth.entity.*;
import com.deskin.global.auth.AuthPrincipal;
import com.deskin.global.exception.*;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class LogoutServiceTest extends AuthServiceTestSupport {
    @Test
    void revokesOnlyAuthenticatedSession() {
        var first = new LoginSession(createUser(UserRole.BUYER), clock.instant().plusSeconds(120));
        var second = new LoginSession(first.getUser(), first.getExpiresAt());
        when(sessions.findLockedById(first.getSessionId())).thenReturn(Optional.of(first));
        service.revokeSession(new AuthPrincipal(12L, UserRole.BUYER, first.getSessionId()));
        assertThat(first.getRevokedAt()).isEqualTo(clock.instant());
        assertThat(second.isActive(clock.instant())).isTrue();
        verify(sessions, never()).findLockedById(second.getSessionId());
        verifyNoInteractions(refreshTokens);
    }

    @Test
    void rejectsSessionBelongingToAnotherUserOrRole() {
        var session = new LoginSession(createUser(UserRole.BUYER), clock.instant().plusSeconds(120));
        when(sessions.findLockedById(session.getSessionId())).thenReturn(Optional.of(session));
        assertRejected(new AuthPrincipal(13L, UserRole.BUYER, session.getSessionId()));
        assertRejected(new AuthPrincipal(12L, UserRole.ADMIN, session.getSessionId()));
        assertThat(session.getRevokedAt()).isNull();
    }

    @Test
    void rejectsMissingExpiredAndRevokedSessions() {
        var session = new LoginSession(createUser(UserRole.BUYER), clock.instant());
        var principal = new AuthPrincipal(12L, UserRole.BUYER, session.getSessionId());
        assertRejected(principal);
        when(sessions.findLockedById(session.getSessionId())).thenReturn(Optional.of(session));
        assertRejected(principal);
        session.revoke(clock.instant());
        assertRejected(principal);
    }

    private void assertRejected(AuthPrincipal principal) {
        assertThatThrownBy(() -> service.revokeSession(principal)).isInstanceOfSatisfying(CustomException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.SESSION_REVOKED));
    }
}
