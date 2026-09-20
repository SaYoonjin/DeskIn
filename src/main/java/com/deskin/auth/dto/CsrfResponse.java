package com.deskin.auth.dto;

public record CsrfResponse(String csrfToken, String headerName) {}
