package com.deskin.global.exception;

public class RefreshTokenReuseException extends CustomException {
    public RefreshTokenReuseException() {
        super(ErrorCode.REFRESH_TOKEN_REUSED);
    }
}
