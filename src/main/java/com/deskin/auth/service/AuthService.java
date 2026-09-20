package com.deskin.auth.service;

import com.deskin.auth.dto.LoginRequest;
import com.deskin.auth.dto.TokenResult;
import com.deskin.global.auth.AuthPrincipal;
import com.deskin.auth.dto.SignupRequest;
import com.deskin.auth.dto.SignupResponse;

public interface AuthService {
    SignupResponse createUser(SignupRequest request);
    TokenResult authenticateUser(LoginRequest request);
    TokenResult refreshTokens(String refreshToken);
    void revokeSession(AuthPrincipal principal);
}
