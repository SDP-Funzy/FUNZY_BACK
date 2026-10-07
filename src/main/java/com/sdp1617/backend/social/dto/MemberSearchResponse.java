package com.sdp1617.backend.social.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record MemberSearchResponse(
        @Schema(description = "회원 ID (편지 보내기의 recipientMemberId)", example = "2")
        Long memberId,
        @Schema(description = "닉네임(아이디)", example = "tiki")
        String nickname,
        @Schema(description = "프로필 이미지 URL. 기본 이미지면 null")
        String profileImageUrl,
        @Schema(description = "나와 친구(맞팔)인지")
        boolean friend
) {
}
