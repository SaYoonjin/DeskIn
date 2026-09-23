package com.deskin.auth;

import com.deskin.global.exception.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class LogoutServiceTest extends AuthServiceTestSupport {
    @Test
    void deletesOnlyProvidedTokenHashAndAllowsRepeatedLogout() {
        String raw = opaqueTokens.createToken();
        service.logout(raw);
        service.logout(raw);
        verify(refreshTokens, times(2)).deleteByTokenHash(opaqueTokens.hashToken(raw));
        verifyNoMoreInteractions(refreshTokens);
        verifyNoInteractions(users);
    }

    @Test
    void rejectsInvalidTokenFormatBeforeDatabaseAccess() {
        assertThatThrownBy(() -> service.logout("invalid")).isInstanceOfSatisfying(CustomException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN));
        verifyNoInteractions(refreshTokens);
    }
}
