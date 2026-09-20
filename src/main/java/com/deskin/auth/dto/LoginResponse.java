package com.deskin.auth.dto;

import com.deskin.auth.entity.UserRole;

public record LoginResponse(String accessToken, Long userId, UserRole role) {}
