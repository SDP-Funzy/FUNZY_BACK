package com.sdp1617.backend.letter.dto;

import com.sdp1617.backend.auth.entity.Member;
import io.swagger.v3.oas.annotations.media.Schema;

/** 편지의 보낸/받는 회원. 탈퇴한 회원은 닉네임이 "탈퇴한회원N"으로 보인다. */
public record LetterMemberResponse(
        @Schema(description = "회원 ID", example = "2")
        Long memberId,
        @Schema(description = "닉네임(아이디)", example = "tiki")
        String nickname,
        @Schema(description = "프로필 이미지 URL. 기본 이미지면 null")
        String profileImageUrl
) {
    public static LetterMemberResponse from(Member member) {
        if (member == null) {
            return null;
        }
        return new LetterMemberResponse(member.getId(), member.getNickname(), member.getProfileImageUrl());
    }
}
