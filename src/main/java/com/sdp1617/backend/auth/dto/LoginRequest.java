package com.sdp1617.backend.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @Schema(description = "아이디 (가입 시 등록한 닉네임)", example = "닉네임")
        @NotBlank(message = "아이디를 입력해주세요.")
        String nickname,

        @Schema(description = "비밀번호", example = "Password1!")
        @NotBlank(message = "비밀번호를 입력해주세요.")
        String password
) {
    public LoginRequest {
        nickname = nickname == null ? null : nickname.trim();
    }
}
