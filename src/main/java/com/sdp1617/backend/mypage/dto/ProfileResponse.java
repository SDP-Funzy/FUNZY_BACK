package com.sdp1617.backend.mypage.dto;

import com.sdp1617.backend.auth.entity.AuthProvider;
import io.swagger.v3.oas.annotations.media.Schema;

public record ProfileResponse(
        @Schema(description = "닉네임(아이디)", example = "sweet.day")
        String nickname,

        @Schema(description = "프로필 이미지 URL. 기본 이미지 상태면 null", example = "https://sdp-funzy.s3.ap-northeast-2.amazonaws.com/profiles/1/abc.jpg")
        String profileImageUrl,

        @Schema(description = "가입 방식", example = "LOCAL")
        AuthProvider provider
) {
}
