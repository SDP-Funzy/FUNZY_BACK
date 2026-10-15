package com.sdp1617.backend.notification.entity;

public enum NotificationType {
    /** 편지 도착 (MY-312) */
    LETTER,
    /** 아카이브 카드 댓글·답글 (#164): 내 카드에 남이 댓글, 내 댓글에 답글 */
    COMMENT,
    /** 두들픽 선물 선택 (LR-023) */
    GIFT_SELECTED,
    /** 친구 요청 받음 (FR-011) */
    FOLLOW_REQUEST,
    /** 친구 요청 수락됨 (FR-012, LB-222) */
    FOLLOW_ACCEPTED
}
