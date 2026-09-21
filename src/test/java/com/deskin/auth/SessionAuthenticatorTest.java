package com.deskin.auth;

import com.deskin.auth.entity.*;
import com.deskin.auth.security.AuthPrincipal;
import com.deskin.auth.security.SessionAuthenticator;
import com.deskin.global.exception.*;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SessionAuthenticatorTest extends AuthServiceTestSupport {
    @Test
    void acceptsOnlyActiveSessionWithMatchingUserAndRole() {
        var session = new LoginSession(createUser(UserRole.BUYER), clock.instant().plusSeconds(60));
        when(sessions.findById(session.getSessionId())).thenReturn(Optional.of(session));
        var authenticator = new SessionAuthenticator(sessions, clock);
        authenticator.validateSession(new AuthPrincipal(12L, UserRole.BUYER, session.getSessionId()));
        assertRejected(() -> authenticator.validateSession(new AuthPrincipal(13L, UserRole.BUYER, session.getSessionId())));
        assertRejected(() -> authenticator.validateSession(new AuthPrincipal(12L, UserRole.ADMIN, session.getSessionId())));
        session.revoke(clock.instant());
        assertRejected(() -> authenticator.validateSession(new AuthPrincipal(12L, UserRole.BUYER, session.getSessionId())));
    }

    @Test
    void rejectsMissingAndExpiredSessions() {
        var session = new LoginSession(createUser(UserRole.BUYER), clock.instant());
        var authenticator = new SessionAuthenticator(sessions, clock);
        var principal = new AuthPrincipal(12L, UserRole.BUYER, session.getSessionId());
        assertRejected(() -> authenticator.validateSession(principal));
        when(sessions.findById(session.getSessionId())).thenReturn(Optional.of(session));
        assertRejected(() -> authenticator.validateSession(principal));
    }

    private void assertRejected(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(CustomException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.SESSION_REVOKED));
    }
}
