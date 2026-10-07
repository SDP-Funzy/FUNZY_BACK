package com.sdp1617.backend.letter.service;

import com.sdp1617.backend.letter.entity.Letter;

/**
 * 받는 사람이 받은 편지에 반응했다. 보낸 사람에게 알림을 만든다.
 * 이모지는 새로 남길 때, 코멘트는 남길 때마다 발행한다 (수정·삭제는 발행 안 함).
 * 이모지 알림은 알림 쪽에서 마음카드마다(두들픽 선물은 편지마다) 한 번만 만든다.
 */
public record LetterReactedEvent(
        Kind kind,
        Long letterId,
        Long cardId,
        Long senderId,
        Long recipientId,
        String recipientName
) {

    public enum Kind {
        /** 마음카드 이모지 (LR-114) */
        CARD_EMOJI,
        /** 마음카드 문구 코멘트 (LR-224) */
        CARD_COMMENT,
        /** 두들픽 선물 후보 이모지 (LR-614) */
        GIFT_EMOJI
    }

    /** cardId는 마음카드 반응일 때만 있다. */
    public static LetterReactedEvent of(Kind kind, Letter letter, Long cardId) {
        return new LetterReactedEvent(kind, letter.getId(), cardId, letter.getSender().getId(),
                letter.getRecipient().getId(), letter.getToName());
    }
}
