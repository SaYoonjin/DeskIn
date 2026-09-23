package com.deskin.global.exception;

import java.util.List;

public record ErrorResponse(boolean success, String message, ErrorDetail error) {
    public static ErrorResponse of(ErrorCode errorCode) {
        return of(errorCode, List.of());
    }

    public static ErrorResponse of(ErrorCode errorCode, List<FieldError> fieldErrors) {
        return new ErrorResponse(false, errorCode.getMessage(),
                new ErrorDetail(errorCode.name(), List.copyOf(fieldErrors)));
    }

    public record ErrorDetail(String code, List<FieldError> fieldErrors) {}

    public record FieldError(String field, String message) {}
}
