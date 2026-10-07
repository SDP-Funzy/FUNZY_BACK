package com.sdp1617.backend.letter.service;

/** 받는 사람이 두들픽 선물을 골랐다 (LR-022). 보낸 사람에게 알림을 만든다 (LR-023). */
public record GiftSelectedEvent(
        Long letterId,
        Long senderId,
        Long recipientId,
        String recipientName
) {
}
