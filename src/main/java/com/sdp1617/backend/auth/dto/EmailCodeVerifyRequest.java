package com.sdp1617.backend.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.Locale;

public record EmailCodeVerifyRequest(
        @Schema(description = "인증번호를 받은 이메일", example = "test@sdp1617.com")
        @NotBlank(message = "이메일을 입력해주세요.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        String email,

        @Schema(description = "메일로 받은 6자리 인증번호", example = "123456")
        @NotBlank(message = "인증번호를 입력해주세요.")
        @Pattern(regexp = "\\d{6}", message = "인증번호는 6자리 숫자입니다.")
        String code
) {
    public EmailCodeVerifyRequest {
        email = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
        code = code == null ? null : code.trim();
    }
}
