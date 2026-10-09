package com.sdp1617.backend.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record NicknameCheckResponse(
        @Schema(description = "아이디 사용 가능 여부", example = "true")
        boolean available
) {
}
