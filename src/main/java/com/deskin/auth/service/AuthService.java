package com.deskin.auth.service;

import com.deskin.auth.dto.*;

public interface AuthService {
    SignupResponse createUser(SignupRequest request);
    LoginResponse authenticateUser(LoginRequest request);
    TokenRefreshResponse refreshAccessToken(String refreshToken);
    void logout(String refreshToken);
}
