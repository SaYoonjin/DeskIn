package com.deskin.global.auth;

import com.deskin.auth.repository.LoginSessionRepository;
import com.deskin.global.exception.CustomException;
import com.deskin.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;

@Component
@RequiredArgsConstructor
public class SessionAuthenticator {
    private final LoginSessionRepository sessions;
    private final Clock clock;

    @Transactional(readOnly = true)
    public void validateSession(AuthPrincipal principal) {
        var session = sessions.findById(principal.sessionId())
                .orElseThrow(() -> new CustomException(ErrorCode.SESSION_REVOKED));
        if (!session.isActive(clock.instant()) || !session.getUser().getUserId().equals(principal.userId())
                || session.getUser().getRole() != principal.role()) {
            throw new CustomException(ErrorCode.SESSION_REVOKED);
        }
    }
}
