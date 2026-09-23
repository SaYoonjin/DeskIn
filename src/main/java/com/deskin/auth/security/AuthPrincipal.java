package com.deskin.auth.security;

import com.deskin.auth.entity.UserRole;
import java.security.Principal;

public record AuthPrincipal(Long userId, UserRole role) implements Principal {
    @Override
    public String getName() {
        return userId.toString();
    }
}
