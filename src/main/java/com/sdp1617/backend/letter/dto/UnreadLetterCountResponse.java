package com.sdp1617.backend.letter.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 받은 편지함의 미읽음 편지 수 (LB-221). */
public record UnreadLetterCountResponse(
        @Schema(description = "아직 열지 않은 받은 편지 수. 받은 편지함에서 지운 편지는 세지 않음", example = "2")
        long unreadCount
) {
}
