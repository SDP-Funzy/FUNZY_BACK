package com.sdp1617.backend.heartcard.dto;

import com.sdp1617.backend.heartcard.entity.HeartCardEmojiAction;
import com.sdp1617.backend.heartcard.entity.HeartCardEmojiReaction;
import com.sdp1617.backend.heartcard.entity.HeartCardEmojiType;
import io.swagger.v3.oas.annotations.media.Schema;

public record HeartCardEmojiResponse(
        Long heartCardId,
        HeartCardEmojiType emoji,
        boolean reacted,
        HeartCardEmojiAction action,
        @Schema(description = "이번 요청으로 보낸 사람에게 알림을 만들었는지. 이모지를 처음 남길 때만 true (같은 카드는 지웠다 다시 남겨도 한 번만, 보낸 사람이 탈퇴했으면 false). 수정·삭제·조회는 false")
        boolean notificationCreated
) {
    public static HeartCardEmojiResponse empty(Long heartCardId) {
        return new HeartCardEmojiResponse(heartCardId, null, false, HeartCardEmojiAction.NONE, false);
    }

    public static HeartCardEmojiResponse from(HeartCardEmojiReaction reaction) {
        return new HeartCardEmojiResponse(reaction.getHeartCardId(), reaction.getEmoji(), true, HeartCardEmojiAction.NONE, false);
    }

    public static HeartCardEmojiResponse of(
            Long heartCardId,
            HeartCardEmojiType emoji,
            HeartCardEmojiAction action,
            boolean notificationCreated
    ) {
        return new HeartCardEmojiResponse(heartCardId, emoji, emoji != null, action, notificationCreated);
    }
}
