package com.deskin.auth.dto;

import com.deskin.auth.entity.UserRole;

public record SignupResponse(Long userId, String name, UserRole role) {}
