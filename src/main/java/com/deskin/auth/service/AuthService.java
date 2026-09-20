package com.deskin.auth.service;

import com.deskin.auth.dto.SignupRequest;
import com.deskin.auth.dto.SignupResponse;

public interface AuthService {
    SignupResponse createUser(SignupRequest request);
}
