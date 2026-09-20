package com.deskin.auth.dto;

import com.deskin.auth.entity.UserRole;
import com.deskin.auth.validation.PasswordBytes;
import jakarta.validation.constraints.*;
import java.util.Locale;

public record SignupRequest(
        @NotBlank(message = "아이디는 필수입니다.")
        @Pattern(regexp = "[a-z0-9_]{4,30}", message = "아이디는 영문·숫자·밑줄 4~30자여야 합니다.") String id,
        @NotBlank(message = "비밀번호는 필수입니다.")
        @Size(min = 10, max = 64, message = "비밀번호는 10~64자여야 합니다.") @PasswordBytes String password,
        @NotBlank(message = "이름은 필수입니다.") @Size(max = 50, message = "이름은 50자 이하여야 합니다.") String name,
        @Pattern(regexp = "\\+?[0-9]{8,15}", message = "전화번호 형식이 올바르지 않습니다.") String phone,
        @NotNull(message = "계정 유형은 필수입니다.") UserRole role,
        @NotBlank(message = "이메일은 필수입니다.") @Email(message = "이메일 형식이 올바르지 않습니다.")
        @Size(max = 254, message = "이메일은 254자 이하여야 합니다.") String email,
        @Size(max = 100, message = "상점명은 100자 이하여야 합니다.") String storeName
) {
    public SignupRequest {
        id = id == null ? null : id.strip().toLowerCase(Locale.ROOT);
        name = name == null ? null : name.strip();
        email = email == null ? null : email.strip().toLowerCase(Locale.ROOT);
        storeName = storeName == null ? null : storeName.strip();
        phone = phone == null || phone.isBlank() ? null : phone.replace(" ", "").replace("-", "");
    }
}
