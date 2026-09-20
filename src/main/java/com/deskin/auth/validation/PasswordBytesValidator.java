package com.deskin.auth.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.nio.charset.StandardCharsets;

public class PasswordBytesValidator implements ConstraintValidator<PasswordBytes, String> {
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        // BCrypt의 입력 한계를 넘어 서로 다른 비밀번호가 같은 값으로 취급되는 것을 막는다.
        return value == null || value.getBytes(StandardCharsets.UTF_8).length <= 72;
    }
}
