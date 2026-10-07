package com.sdp1617.backend.letter.dto;

import com.sdp1617.backend.letter.entity.Letter;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record DoodlePickResponse(
        @Schema(description = "선정 이유")
        String reason,
        List<GiftItemResponse> giftItems
) {
    public record GiftItemResponse(
            @Schema(description = "선물 후보 ID (받는 쪽 선물 고르기·선물 항목 이모지에 사용)", example = "3")
            Long giftItemId,
            int itemOrder,
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
