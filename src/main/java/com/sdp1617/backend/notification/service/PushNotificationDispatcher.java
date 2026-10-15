package com.sdp1617.backend.notification.service;

import com.sdp1617.backend.auth.entity.Member;
import com.sdp1617.backend.auth.repository.MemberRepository;
import com.sdp1617.backend.global.config.properties.PushProperties;
import com.sdp1617.backend.notification.entity.NotificationType;
import com.sdp1617.backend.notification.entity.PushSubscription;
import com.sdp1617.backend.notification.push.PushSendResult;
import com.sdp1617.backend.notification.push.PushSender;
import com.sdp1617.backend.notification.repository.PushSubscriptionRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.databind.ObjectMapper;

/**
 * 알림함에 알림이 생기면 그 회원의 브라우저들로 웹 푸시를 보낸다 (#89, MY-310, MY-312~314).
 * 알림이 커밋된 뒤 별도 스레드에서 보낸다 — 푸시 서비스가 느리거나 실패해도 요청과 알림 저장에는 영향이 없다.
 * 푸시 수신 설정(pushNotificationEnabled)을 끈 회원에게는 보내지 않는다. 만료된 구독은 지운다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PushNotificationDispatcher {

    static final String TITLE = "Funzy";

    private final PushProperties pushProperties;
    private final PushSender pushSender;
    private final PushSubscriptionRepository pushSubscriptionRepository;
    private final MemberRepository memberRepository;
    private final ObjectMapper objectMapper;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onNotificationCreated(NotificationCreatedEvent event) {
        dispatch(event);
    }

    void dispatch(NotificationCreatedEvent event) {
        if (!pushProperties.enabled() || !wantsPush(event.memberId())) {
            return;
        }
        List<PushSubscription> subscriptions = pushSubscriptionRepository.findByMemberIdOrderByUpdatedAtDesc(event.memberId());
        if (subscriptions.isEmpty()) {
            return;
        }
        byte[] payload = objectMapper.writeValueAsBytes(PushPayload.from(event));
        for (PushSubscription subscription : subscriptions) {
            send(subscription, payload);
        }
    }

    private boolean wantsPush(Long memberId) {
        return memberRepository.findActiveById(memberId).map(Member::isPushNotificationEnabled).orElse(false);
    }

    /** 한 브라우저로 보내다 실패해도 다른 브라우저에는 보낸다. */
    private void send(PushSubscription subscription, byte[] payload) {
        try {
            PushSendResult result = pushSender.send(
                    subscription.getEndpoint(), subscription.getP256dh(), subscription.getAuth(), payload);
            if (result == PushSendResult.EXPIRED) {
                pushSubscriptionRepository.deleteIfUnchanged(subscription.getId(), subscription.getUpdatedAt());
            }
        } catch (RuntimeException e) {
            log.warn("웹 푸시 발송 중 오류: subscriptionId={}", subscription.getId(), e);
        }
    }

    /**
     * 브라우저 Service Worker가 받는 내용. title·body로 알림을 띄우고, 누르면 알림함 응답과 같은 기준으로 이동한다
     * (letterId가 있으면 편지, archiveCardId가 있으면 그 아카이브 카드, 친구 알림은 actorMemberId).
     */
    record PushPayload(String title, String body, Long notificationId, NotificationType type,
                       Long actorMemberId, Long letterId, Long archiveCardId) {

        static PushPayload from(NotificationCreatedEvent event) {
            return new PushPayload(TITLE, event.content(), event.notificationId(), event.type(),
                    event.actorMemberId(), event.letterId(), event.archiveCardId());
        }
    }
}
