package com.sdp1617.backend.notification.entity;

public enum NotificationType {
    /** 편지 도착 (MY-312) */
    LETTER,
    /** 마음카드·두들픽 선물 후보에 이모지 (MY-313, LR-114, LR-614) */
    REACTION,
    /** 마음카드 문구 코멘트 (MY-314, LR-224) */
    COMMENT,
    /** 두들픽 선물 선택 (LR-023) */
    GIFT_SELECTED,
    /** 친구 요청 받음 (FR-011) */
    FOLLOW_REQUEST,
    /** 친구 요청 수락됨 (FR-012, LB-222) */
    FOLLOW_ACCEPTED
}
