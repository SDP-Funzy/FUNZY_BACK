package com.sdp1617.backend.social.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

public record FriendResponse(
        @Schema(description = "친구 회원 ID (팔로우 끊기 시 사용)", example = "2")
        Long memberId,

        @Schema(description = "친구 닉네임", example = "유저B")
        String nickname,

        @Schema(description = "친구 프로필 이미지 URL. 기본 이미지면 null", example = "https://sdp-funzy.s3.ap-northeast-2.amazonaws.com/profiles/2/abc.jpg")
        String profileImageUrl,

        @Schema(description = "친구가 된 시각")
        LocalDateTime friendSince
) {
}
