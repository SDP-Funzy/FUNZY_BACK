package com.sdp1617.backend.notification.service;

import com.sdp1617.backend.notification.entity.Notification;
import com.sdp1617.backend.notification.entity.NotificationType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 읽지 않은 알림 수를 실제 DB로 검증한다 (CI에서는 PostgreSQL). */
@SpringBootTest
@Transactional
class NotificationUnreadCountIntegrationTest {

    @Autowired
    private EntityManager em;

    @Autowired
    private NotificationService notificationService;

    private Notification notification(Long memberId) {
        Notification notification = new Notification(memberId, NotificationType.LETTER, "새 편지가 도착했습니다.");
        em.persist(notification);
        return notification;
    }

    @Test
    void 내_알림_중_읽지_않은_것만_세고_읽음_처리하면_바로_줄어든다() {
        Notification first = notification(1L);
        notification(1L);
        notification(2L);
        em.flush();
        assertEquals(2, notificationService.getUnreadCount(1L).unreadCount());

        notificationService.markAsRead(1L, first.getId());
        em.flush();

        assertEquals(1, notificationService.getUnreadCount(1L).unreadCount());
        assertEquals(1, notificationService.getUnreadCount(2L).unreadCount());
        assertEquals(0, notificationService.getUnreadCount(3L).unreadCount());
    }
}
