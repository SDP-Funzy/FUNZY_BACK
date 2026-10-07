package com.sdp1617.backend.giftitem.dto;

import com.sdp1617.backend.giftitem.entity.GiftItemEmojiReaction;
import com.sdp1617.backend.heartcard.entity.HeartCardEmojiAction;
import com.sdp1617.backend.heartcard.entity.HeartCardEmojiType;
import io.swagger.v3.oas.annotations.media.Schema;

public record GiftItemEmojiResponse(
        Long giftItemId,
        HeartCardEmojiType emoji,
        boolean reacted,
        HeartCardEmojiAction action,
        @Schema(description = "이번 요청으로 보낸 사람에게 알림을 만들었는지. 이 편지의 선물 후보에 이모지를 처음 남길 때만 true (편지마다 한 번, 보낸 사람이 탈퇴했으면 false). 수정·삭제·조회는 false")
        boolean notificationCreated
) {
    public static GiftItemEmojiResponse empty(Long giftItemId) {
        return new GiftItemEmojiResponse(giftItemId, null, false, HeartCardEmojiAction.NONE, false);
    }

    public static GiftItemEmojiResponse from(GiftItemEmojiReaction reaction) {
        return new GiftItemEmojiResponse(reaction.getGiftItemId(), reaction.getEmoji(), true, HeartCardEmojiAction.NONE, false);
    }

    public static GiftItemEmojiResponse of(
            Long giftItemId,
            HeartCardEmojiType emoji,
            HeartCardEmojiAction action,
            boolean notificationCreated
    ) {
        return new GiftItemEmojiResponse(giftItemId, emoji, emoji != null, action, notificationCreated);
    }
}
