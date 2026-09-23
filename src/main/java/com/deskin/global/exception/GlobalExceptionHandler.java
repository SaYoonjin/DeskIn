package com.deskin.global.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataIntegrityViolationException;
import org.hibernate.exception.ConstraintViolationException;
import java.util.Locale;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(CustomException.class)
    public ResponseEntity<ErrorResponse> handleCustomException(CustomException exception) {
        return createResponse(exception.getErrorCode());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException exception) {
        var fieldErrors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new ErrorResponse.FieldError(error.getField(), error.getDefaultMessage()))
                .toList();
        return ResponseEntity.badRequest().body(ErrorResponse.of(ErrorCode.VALIDATION_FAILED, fieldErrors));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableRequest(HttpMessageNotReadableException exception) {
        return createResponse(ErrorCode.VALIDATION_FAILED);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedException(Exception exception) {
        // 내부 예외에 포함될 수 있는 자격 증명과 SQL 정보를 응답으로 전달하지 않는다.
        return createResponse(ErrorCode.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleIntegrityException(DataIntegrityViolationException exception) {
        // 사전 조회 이후 동시에 가입한 요청도 DB 제약 이름으로 같은 오류 계약을 적용한다.
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation && violation.getConstraintName() != null) {
                String constraint = violation.getConstraintName().toLowerCase(Locale.ROOT);
                if (constraint.contains("uk_users_login_id")) {
                    return createResponse(ErrorCode.DUPLICATE_LOGIN_ID);
                }
                if (constraint.contains("uk_users_email")) {
                    return createResponse(ErrorCode.DUPLICATE_EMAIL);
                }
            }
        }
        return createResponse(ErrorCode.INTERNAL_SERVER_ERROR);
    }

    private ResponseEntity<ErrorResponse> createResponse(ErrorCode errorCode) {
        return ResponseEntity.status(errorCode.getStatus()).body(ErrorResponse.of(errorCode));
    }
}
