package com.deskin.global.auth;

import com.deskin.auth.entity.UserRole;
import java.security.Principal;
import java.util.UUID;

public record AuthPrincipal(Long userId, UserRole role, UUID sessionId) implements Principal {
    @Override
    public String getName() {
        return userId.toString();
    }
}
