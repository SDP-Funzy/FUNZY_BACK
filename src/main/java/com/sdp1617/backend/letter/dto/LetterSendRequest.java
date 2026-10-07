package com.sdp1617.backend.letter.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record LetterSendRequest(
        @Schema(description = "받는 회원 ID (친구 목록 또는 회원 검색 결과의 memberId)", example = "2")
        @NotNull(message = "받는 사람은 필수입니다.")
        Long recipientMemberId
) {
}
