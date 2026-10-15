package com.sdp1617.backend.notification.dto;

import com.sdp1617.backend.notification.entity.Notification;
import com.sdp1617.backend.notification.entity.NotificationType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

public record NotificationResponse(
        @Schema(description = "알림 ID", example = "1")
        Long id,

        @Schema(description = "알림 유형: LETTER(편지 도착), GIFT_SELECTED(선물 선택), FOLLOW_REQUEST(친구 요청), FOLLOW_ACCEPTED(친구 요청 수락)", example = "LETTER")
        NotificationType type,

        @Schema(description = "알림 내용", example = "티키님에게서 편지가 도착했어요.")
        String content,

        @Schema(description = "읽음 여부", example = "false")
        boolean read,

        @Schema(description = "알림을 일으킨 회원 ID (보낸 사람·선물 고른 사람·친구 요청한 사람). 없으면 null", example = "7")
        Long actorMemberId,

        @Schema(description = "탭하면 열 편지 ID (편지 열기 API). 편지 관련 알림(LETTER·GIFT_SELECTED)에만 있음", example = "12")
        Long letterId,

        @Schema(description = "탭하면 보여줄 마음카드 ID. 지금 생기는 알림에는 없음(null)", example = "34")
        Long cardId,

        @Schema(description = "알림 생성 시각")
        LocalDateTime createdAt
) {
    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType(),
                notification.getContent(),
                notification.isRead(),
                notification.getActorMemberId(),
                notification.getLetterId(),
                notification.getCardId(),
                notification.getCreatedAt()
        );
    }
}
