package com.sdp1617.backend.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 읽지 않은 알림 수 (LB-222, MY-321). 0보다 크면 알림 점을 표시한다. */
public record UnreadNotificationCountResponse(
        @Schema(description = "읽지 않은 알림 수", example = "3")
        long unreadCount
) {
}
