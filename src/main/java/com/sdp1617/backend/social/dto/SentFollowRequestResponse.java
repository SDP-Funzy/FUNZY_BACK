package com.sdp1617.backend.social.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

public record SentFollowRequestResponse(
        @Schema(description = "팔로우 요청 ID (요청 취소 시 사용)", example = "1")
        Long requestId,

        @Schema(description = "요청을 받은 회원 ID", example = "2")
        Long receiverId,

        @Schema(description = "요청을 받은 회원 닉네임", example = "유저B")
        String receiverNickname,

        @Schema(description = "요청을 받은 회원 프로필 이미지 URL. 기본 이미지면 null", example = "https://sdp-funzy.s3.ap-northeast-2.amazonaws.com/profiles/2/abc.jpg")
        String receiverProfileImageUrl,

        @Schema(description = "요청 생성 시각")
        LocalDateTime createdAt
) {
}
