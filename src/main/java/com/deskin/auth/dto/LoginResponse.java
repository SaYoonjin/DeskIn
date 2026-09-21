package com.deskin.auth.dto;

import com.deskin.auth.entity.UserRole;

public record LoginResponse(String accessToken, String refreshToken, Long userId, UserRole role) {}
