package com.sdp1617.backend.social.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 회원 검색 결과·회원 프로필 조회 공통 (#153). */
public record MemberProfileResponse(
        @Schema(description = "회원 ID (편지 보내기의 recipientMemberId, 팔로우 요청·아카이브 조회에 사용)", example = "2")
        Long memberId,
        @Schema(description = "아이디", example = "tiki")
        String nickname,
        @Schema(description = "프로필 이미지 URL. 기본 이미지면 null")
        String profileImageUrl,
        @Schema(description = "나와의 관계: NONE(요청 가능) / REQUESTED(내가 요청함) / RECEIVED(상대가 요청함) / FRIEND(친구) / SELF(나, 프로필 조회에서만)",
                example = "REQUESTED")
        FollowStatus followStatus,
        @Schema(description = "REQUESTED·RECEIVED일 때 팔로우 요청 ID (취소·수락·거절에 사용). 그 밖에는 null", example = "5")
        Long followRequestId
) {
}
