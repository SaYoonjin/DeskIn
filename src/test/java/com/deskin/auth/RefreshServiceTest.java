package com.deskin.auth;

import com.deskin.auth.entity.*;
import com.deskin.global.exception.*;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RefreshServiceTest extends AuthServiceTestSupport {
    @Test
    void rotatesTokenWithoutExtendingSessionExpiry() {
        String raw = opaqueTokens.createToken();
        var session = new LoginSession(createUser(UserRole.BUYER), clock.instant().plusSeconds(120));
        var token = prepareToken(raw, session);
        var result = service.refreshTokens(raw);
        assertThat(token.getUsedAt()).isEqualTo(clock.instant());
        assertThat(result.refreshToken()).isNotEqualTo(raw);
        assertThat(result.expiresAt()).isEqualTo(session.getExpiresAt());
        assertThat(jwtTokens.parseAccessToken(result.response().accessToken()).sessionId()).isEqualTo(session.getSessionId());
        verify(refreshTokens).save(argThat(saved -> saved.getTokenHash().equals(opaqueTokens.hashToken(result.refreshToken()))
                && saved.getExpiresAt().equals(session.getExpiresAt())));
        var order = inOrder(sessions, refreshTokens);
        order.verify(refreshTokens).findSessionIdByTokenHash(opaqueTokens.hashToken(raw));
        order.verify(sessions).findLockedById(session.getSessionId());
        order.verify(refreshTokens).findById(opaqueTokens.hashToken(raw));
    }

    @Test
    void revokesSessionOnReuseWithoutIssuingToken() {
        String raw = opaqueTokens.createToken();
        var session = new LoginSession(createUser(UserRole.BUYER), clock.instant().plusSeconds(120));
        var token = prepareToken(raw, session);
        token.markUsed(clock.instant().minusSeconds(10));
        assertThatThrownBy(() -> service.refreshTokens(raw)).isInstanceOf(RefreshTokenReuseException.class);
        assertThat(session.getRevokedAt()).isEqualTo(clock.instant());
        verify(refreshTokens, never()).save(any());
    }

    @Test
    void rejectsMissingMalformedAndUnknownToken() {
        for (String raw : new String[]{null, "", "not-a-token", opaqueTokens.createToken()}) {
            assertInvalid(raw);
        }
        verifyNoInteractions(sessions);
        verify(refreshTokens, never()).save(any());
    }

    @Test
    void rejectsExpiredSession() {
        String raw = opaqueTokens.createToken();
        prepareToken(raw, new LoginSession(createUser(UserRole.BUYER), clock.instant()));
        assertInvalid(raw);
        verify(refreshTokens, never()).save(any());
    }

    @Test
    void rejectsRevokedSession() {
        String raw = opaqueTokens.createToken();
        var session = new LoginSession(createUser(UserRole.BUYER), clock.instant().plusSeconds(120));
        session.revoke(clock.instant());
        prepareToken(raw, session);
        assertInvalid(raw);
        verify(refreshTokens, never()).save(any());
    }

    private RefreshToken prepareToken(String raw, LoginSession session) {
        String hash = opaqueTokens.hashToken(raw);
        var token = new RefreshToken(hash, session, clock.instant().minusSeconds(10));
        when(refreshTokens.findSessionIdByTokenHash(hash)).thenReturn(Optional.of(session.getSessionId()));
        when(sessions.findLockedById(session.getSessionId())).thenReturn(Optional.of(session));
        when(refreshTokens.findById(hash)).thenReturn(Optional.of(token));
        return token;
    }

    private void assertInvalid(String raw) {
        assertThatThrownBy(() -> service.refreshTokens(raw)).isInstanceOfSatisfying(CustomException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN));
    }
}
