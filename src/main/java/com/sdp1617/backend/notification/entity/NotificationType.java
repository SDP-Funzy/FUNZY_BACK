package com.sdp1617.backend.notification.entity;

public enum NotificationType {
    /** 편지 도착 (MY-312) */
    LETTER,
    /** 댓글. 문구 코멘트를 없애 지금은 만들지 않음 — 아카이브 카드 댓글 알림(#164)에 다시 쓴다 */
    COMMENT,
    /** 두들픽 선물 선택 (LR-023) */
    GIFT_SELECTED,
    /** 친구 요청 받음 (FR-011) */
    FOLLOW_REQUEST,
    /** 친구 요청 수락됨 (FR-012, LB-222) */
    FOLLOW_ACCEPTED
}
