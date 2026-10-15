package com.sdp1617.backend.notification.service;

import com.sdp1617.backend.notification.entity.Notification;
import com.sdp1617.backend.notification.entity.NotificationType;

/** 알림함에 알림이 생김. 커밋된 뒤 웹 푸시로도 보낸다 (#89). */
public record NotificationCreatedEvent(
        Long notificationId,
        Long memberId,
        NotificationType type,
        String content,
        Long actorMemberId,
        Long letterId,
        Long archiveCardId
) {
    public static NotificationCreatedEvent from(Notification notification) {
        return new NotificationCreatedEvent(notification.getId(), notification.getMemberId(), notification.getType(),
                notification.getContent(), notification.getActorMemberId(), notification.getLetterId(),
                notification.getArchiveCardId());
    }
}
