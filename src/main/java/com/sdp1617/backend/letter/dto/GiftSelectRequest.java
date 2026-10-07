package com.sdp1617.backend.letter.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record GiftSelectRequest(
        @Schema(description = "고를 선물 후보 ID (편지의 doodlePick.giftItems[].giftItemId). null이면 선택 취소", example = "3")
        Long giftItemId
) {
}
