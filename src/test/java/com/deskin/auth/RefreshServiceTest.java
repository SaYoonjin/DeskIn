package com.deskin.auth;

import com.deskin.auth.entity.*;
import com.deskin.global.exception.*;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RefreshServiceTest extends AuthServiceTestSupport {
    @Test
    void repeatedlyIssuesOnlyAccessTokenWithoutChangingRefreshToken() {
        String raw = opaqueTokens.createToken();
        var user = createUser(UserRole.BUYER);
        var token = new RefreshToken(opaqueTokens.hashToken(raw), user, clock.instant().plusSeconds(600));
        when(refreshTokens.findById(token.getTokenHash())).thenReturn(Optional.of(token));
        var first = service.refreshAccessToken(raw);
        var second = service.refreshAccessToken(raw);
        assertThat(jwtTokens.parseAccessToken(first.accessToken()).userId()).isEqualTo(12L);
        assertThat(jwtTokens.parseAccessToken(second.accessToken()).role()).isEqualTo(UserRole.BUYER);
        assertThat(token.getExpiresAt()).isEqualTo(clock.instant().plusSeconds(600));
        verify(refreshTokens, times(2)).findById(token.getTokenHash());
        verifyNoMoreInteractions(refreshTokens);
    }

    @Test
    void rejectsMalformedTokenBeforeDatabaseLookup() {
        for (String raw : new String[]{null, "", "invalid"}) assertInvalid(raw);
        verifyNoInteractions(refreshTokens);
    }

    @Test
    void rejectsMissingExpiredAndExactlyExpiredTokens() {
        String raw = opaqueTokens.createToken();
        String hash = opaqueTokens.hashToken(raw);
        when(refreshTokens.findById(hash)).thenReturn(Optional.empty());
        assertInvalid(raw);
        for (long offset : new long[]{-1, 0}) {
            when(refreshTokens.findById(hash)).thenReturn(Optional.of(new RefreshToken(hash,
                    createUser(UserRole.BUYER), clock.instant().plusSeconds(offset))));
            assertInvalid(raw);
        }
    }

    private void assertInvalid(String raw) {
        assertThatThrownBy(() -> service.refreshAccessToken(raw)).isInstanceOfSatisfying(CustomException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN));
    }
}
