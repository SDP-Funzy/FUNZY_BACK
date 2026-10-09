package com.sdp1617.backend.social.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

public record FollowRequestResponse(
        @Schema(description = "팔로우 요청 ID (수락/거절 시 사용)", example = "1")
        Long requestId,

        @Schema(description = "요청을 보낸 회원 ID", example = "2")
        Long requesterId,

        @Schema(description = "요청을 보낸 회원 닉네임", example = "user_b")
        String requesterNickname,

        @Schema(description = "요청 생성 시각")
        LocalDateTime createdAt
) {
}
