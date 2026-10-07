package com.sdp1617.backend.letter.service;

/** 편지를 회원에게 보냈다 (#86). 받는 사람에게 편지 도착 알림을 만든다 (MY-312). */
public record LetterSentEvent(
        Long letterId,
        Long senderId,
        Long recipientId,
        String senderName
) {
}
