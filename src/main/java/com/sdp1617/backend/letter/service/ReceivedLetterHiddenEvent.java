package com.sdp1617.backend.letter.service;

/** 받는 사람이 받은 편지함에서 편지를 지웠다 (LR-512). 그 편지로 받은 알림도 함께 지운다 (누르면 열 수 없는 편지라서). */
public record ReceivedLetterHiddenEvent(
        Long letterId,
        Long recipientId
) {
}
