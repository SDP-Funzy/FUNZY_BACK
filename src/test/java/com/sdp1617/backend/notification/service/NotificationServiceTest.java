package com.sdp1617.backend.notification.service;

import com.sdp1617.backend.auth.entity.Consent;
import com.sdp1617.backend.auth.entity.Member;
import com.sdp1617.backend.auth.repository.MemberRepository;
import com.sdp1617.backend.global.error.CustomException;
import com.sdp1617.backend.global.error.ErrorCode;
import com.sdp1617.backend.notification.dto.NotificationResponse;
import com.sdp1617.backend.notification.dto.PushSettingResponse;
import com.sdp1617.backend.notification.entity.Notification;
import com.sdp1617.backend.notification.entity.NotificationType;
import com.sdp1617.backend.notification.repository.NotificationRepository;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private NotificationRepository notificationRepository;

    @InjectMocks
    private NotificationService notificationService;

    private Member member() {
        return new Member("test@sdp1617.com", "encoded", "닉네임", Consent.requiredOnly());
    }

    private void setId(Object entity, Class<?> type, Long id) {
        try {
            Field field = type.getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void 푸시_알림_설정을_조회한다() {
        Member member = member();
        when(memberRepository.findActiveById(1L)).thenReturn(Optional.of(member));

        PushSettingResponse response = notificationService.getPushSetting(1L);

        assertTrue(response.pushNotificationEnabled());
    }

    @Test
    void 푸시_알림_설정을_변경한다() {
        Member member = member();
        when(memberRepository.findActiveByIdForUpdate(1L)).thenReturn(Optional.of(member));

        notificationService.updatePushSetting(1L, false);

        assertFalse(member.isPushNotificationEnabled());
    }

    @Test
    void 존재하지_않는_회원의_설정_조회시_AUTH_002_예외를_던진다() {
        when(memberRepository.findActiveById(1L)).thenReturn(Optional.empty());

        CustomException exception = assertThrows(CustomException.class,
                () -> notificationService.getPushSetting(1L));

        assertEquals(ErrorCode.AUTH_002, exception.getErrorCode());
    }

    @Test
    void 알림_목록을_최신순으로_조회한다() {
        Notification notification = new Notification(1L, NotificationType.LETTER, "새 편지가 도착했습니다.");
        setId(notification, Notification.class, 10L);
        when(notificationRepository.findByMemberIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(notification));

        List<NotificationResponse> responses = notificationService.getNotifications(1L);

        assertEquals(1, responses.size());
        assertEquals(NotificationType.LETTER, responses.get(0).type());
        assertFalse(responses.get(0).read());
    }

    @Test
    void 알림을_읽음_처리한다() {
        Notification notification = new Notification(1L, NotificationType.LETTER, "새 편지가 도착했습니다.");
        setId(notification, Notification.class, 10L);
        when(notificationRepository.findById(10L)).thenReturn(Optional.of(notification));

        notificationService.markAsRead(1L, 10L);

        assertTrue(notification.isRead());
    }

    @Test
    void 다른_회원의_알림을_읽음처리하면_NOTIFICATION_001_예외를_던진다() {
        Notification notification = new Notification(2L, NotificationType.LETTER, "새 편지가 도착했습니다.");
        setId(notification, Notification.class, 10L);
        when(notificationRepository.findById(10L)).thenReturn(Optional.of(notification));

        CustomException exception = assertThrows(CustomException.class,
                () -> notificationService.markAsRead(1L, 10L));

        assertEquals(ErrorCode.NOTIFICATION_001, exception.getErrorCode());
    }

    @Test
    void 존재하지_않는_알림을_읽음처리하면_NOTIFICATION_001_예외를_던진다() {
        when(notificationRepository.findById(10L)).thenReturn(Optional.empty());

        CustomException exception = assertThrows(CustomException.class,
                () -> notificationService.markAsRead(1L, 10L));

        assertEquals(ErrorCode.NOTIFICATION_001, exception.getErrorCode());
    }
}
