package com.sdp1617.backend.letter.entity;

/** 편지 상태. 작성 중 → 완료(전송 전, 카드 1장 이상) → 전송됨. 전송된 편지는 수정할 수 없다. */
public enum LetterStatus {
    DRAFT,
    COMPLETED,
    SENT
}
