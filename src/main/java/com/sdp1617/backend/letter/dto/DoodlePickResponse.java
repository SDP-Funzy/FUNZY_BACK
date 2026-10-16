package com.sdp1617.backend.letter.dto;

import com.sdp1617.backend.letter.entity.Letter;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record DoodlePickResponse(
        @Schema(description = "선정 이유")
        String reason,
        @Schema(description = "선물 후보 2~3개 (순서대로)")
        List<GiftItemResponse> giftItems
) {
    public record GiftItemResponse(
            @Schema(description = "선물 후보 ID (받는 쪽 선물 고르기에 사용)", example = "3")
            Long giftItemId,
            @Schema(description = "후보 순서 (1부터)", example = "1")
            int itemOrder,
            @Schema(description = "선물 이름", example = "향초")
            String name
    ) {
    }

    /** 두들픽이 없으면 null. */
    public static DoodlePickResponse from(Letter letter) {
        if (!letter.hasDoodlePick()) {
            return null;
        }
        return new DoodlePickResponse(letter.getGiftReason(), letter.getGiftItems().stream()
                .map(item -> new GiftItemResponse(item.getId(), item.getItemOrder(), item.getName()))
                .toList());
    }
}
