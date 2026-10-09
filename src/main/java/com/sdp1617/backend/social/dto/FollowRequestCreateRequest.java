package com.sdp1617.backend.social.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record FollowRequestCreateRequest(
        @Schema(description = "친구 요청을 보낼 회원 ID. 닉네임(아이디) 검색(GET /api/social/members/search) 결과의 memberId", example = "7")
        @NotNull(message = "친구 요청을 보낼 회원을 선택해주세요.")
        Long memberId
) {
}
