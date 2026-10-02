package com.sdp1617.backend.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.Locale;

public record LoginIdFindRequest(
        @Schema(description = "가입할 때 인증한 이메일", example = "test@sdp1617.com")
        @NotBlank(message = "이메일을 입력해주세요.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        String email
) {
    public LoginIdFindRequest {
        email = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
