package com.sdp1617.backend.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record EmailCodeVerifyResponse(
        @Schema(description = "이메일 인증 완료 토큰 (회원가입 요청에 그대로 전달, 30분 유효)",
                example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890")
        String verificationToken
) {
}
