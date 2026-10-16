package com.sdp1617.backend.social.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record FollowCountResponse(
        @Schema(description = "현재 친구(맞팔) 수", example = "1")
        long currentCount,

        @Schema(description = "친구 수 상한 (서버 설정, 기본 50명). 화면에는 이 값을 쓰세요", example = "50")
        int maxCount
) {
}
