package com.sdp1617.backend.notification.service;

import com.sdp1617.backend.auth.entity.Member;
import com.sdp1617.backend.auth.repository.MemberRepository;
import com.sdp1617.backend.global.error.CustomException;
import com.sdp1617.backend.global.error.ErrorCode;
import com.sdp1617.backend.notification.dto.NotificationResponse;
import com.sdp1617.backend.notification.dto.PushSettingResponse;
import com.sdp1617.backend.notification.entity.Notification;
import com.sdp1617.backend.notification.repository.NotificationRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    private final MemberRepository memberRepository;
    private final NotificationRepository notificationRepository;

    public PushSettingResponse getPushSetting(Long memberId) {
        Member member = memberRepository.findActiveById(memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.AUTH_002));

        return new PushSettingResponse(member.isPushNotificationEnabled());
    }

    @Transactional
    public void updatePushSetting(Long memberId, boolean pushNotificationEnabled) {
        Member member = memberRepository.findActiveByIdForUpdate(memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.AUTH_002));

        member.updatePushNotificationEnabled(pushNotificationEnabled);
    }

    public List<NotificationResponse> getNotifications(Long memberId) {
        return notificationRepository.findByMemberIdOrderByCreatedAtDesc(memberId).stream()
                .map(NotificationResponse::from)
                .toList();
    }

    @Transactional
    public void markAsRead(Long memberId, Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .filter(n -> n.isOwnedBy(memberId))
                .orElseThrow(() -> new CustomException(ErrorCode.NOTIFICATION_001));

        notification.markAsRead();
    }
}
