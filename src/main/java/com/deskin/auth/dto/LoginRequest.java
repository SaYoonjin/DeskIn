package com.deskin.auth.dto;

import com.deskin.auth.validation.PasswordBytes;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Locale;

public record LoginRequest(@NotBlank(message = "아이디는 필수입니다.") @Size(max = 30) String id,
                           @NotBlank(message = "비밀번호는 필수입니다.") @Size(max = 64) @PasswordBytes String password) {
    public LoginRequest {
        id = id == null ? null : id.strip().toLowerCase(Locale.ROOT);
    }
}
