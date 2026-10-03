package com.sdp1617.backend.auth.dto;

import com.sdp1617.backend.auth.util.Emails;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record AccountUnlockRequest(
        @Schema(description = "잠금 해제 링크를 받을 가입 이메일", example = "test@sdp1617.com")
        @NotBlank(message = "이메일을 입력해주세요.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        String email
) {
    public AccountUnlockRequest {
        email = Emails.normalize(email);
    }
}
