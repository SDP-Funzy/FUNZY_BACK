package com.sdp1617.backend.notification.service;

import com.sdp1617.backend.auth.entity.Consent;
import com.sdp1617.backend.auth.entity.Member;
import com.sdp1617.backend.notification.entity.Notification;
import com.sdp1617.backend.notification.entity.NotificationType;
import com.sdp1617.backend.notification.entity.PushSubscription;
import com.sdp1617.backend.notification.push.PushSendResult;
import com.sdp1617.backend.notification.push.PushSender;
import com.sdp1617.backend.notification.repository.PushSubscriptionRepository;
import com.sdp1617.backend.social.service.FollowRequestedEvent;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 알림이 생기면 커밋 후 회원의 브라우저들로 웹 푸시를 보낸다 (#89).
 * 커밋 후·비동기 발송을 그대로 검증하려고 테스트 트랜잭션 없이 실행하고, 만든 데이터는 직접 지운다.
 */
@SpringBootTest(properties = {
        "app.push.enabled=true",
        // RFC 8291 부록 A의 키 쌍 (테스트용). 발송은 목으로 바꾸므로 실제로 쓰이지 않는다
        "app.push.vapid-public-key=BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8",
        "app.push.vapid-private-key=yfWPiYE-n46HLnH0KqZOF1fJJU3MYrct3AELtAQ-oRw",
        "app.push.subject=mailto:push@test.example"
})
class PushNotificationDispatcherTest {

    private static final String P256DH =
            "BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4";
    private static final String AUTH = "BTBZMqHH6r4Tts7J_aSIgg";

    @MockitoBean private PushSender pushSender;

    @Autowired private PushNotificationDispatcher dispatcher;
    @Autowired private PushSubscriptionRepository pushSubscriptionRepository;
    @Autowired private ApplicationEventPublisher eventPublisher;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private EntityManager em;
    @Autowired private ObjectMapper objectMapper;

    private final List<Long> memberIds = new ArrayList<>();

    private Member member(String name, boolean pushEnabled) {
        Member saved = transactionTemplate.execute(status -> {
            Member member = new Member(name + "@push.test", "encoded", name, Consent.requiredOnly());
            member.updatePushNotificationEnabled(pushEnabled);
            em.persist(member);
            return member;
        });
        memberIds.add(saved.getId());
        return saved;
    }

    private void subscribe(Member member, String endpoint) {
        pushSubscriptionRepository.save(new PushSubscription(member.getId(), endpoint, P256DH, AUTH));
    }

    private NotificationCreatedEvent event(Member member) {
        return new NotificationCreatedEvent(1L, member.getId(), NotificationType.LETTER,
                "티키님에게서 편지가 도착했어요.", 7L, 12L, null);
    }

    @AfterEach
    void cleanUp() {
        transactionTemplate.executeWithoutResult(status -> {
            em.createQuery("delete from Notification n where n.memberId in :ids")
                    .setParameter("ids", memberIds).executeUpdate();
            em.createQuery("delete from PushSubscription s where s.memberId in :ids")
                    .setParameter("ids", memberIds).executeUpdate();
            memberIds.forEach(id -> em.remove(em.find(Member.class, id)));
        });
    }

    @Test
    void 회원의_모든_브라우저로_알림_내용을_보낸다() throws Exception {
        Member member = member("pushall", true);
        subscribe(member, "https://fcm.googleapis.com/fcm/send/all-a");
        subscribe(member, "https://updates.push.services.mozilla.com/wpush/v2/all-b");
        when(pushSender.send(anyString(), anyString(), anyString(), any())).thenReturn(PushSendResult.SENT);

        dispatcher.dispatch(event(member));

        ArgumentCaptor<byte[]> payload = ArgumentCaptor.forClass(byte[].class);
        verify(pushSender).send(eq("https://fcm.googleapis.com/fcm/send/all-a"), eq(P256DH), eq(AUTH), payload.capture());
        verify(pushSender).send(eq("https://updates.push.services.mozilla.com/wpush/v2/all-b"), eq(P256DH), eq(AUTH), any());
        JsonNode json = objectMapper.readTree(new String(payload.getValue(), StandardCharsets.UTF_8));
        assertEquals("Funzy", json.get("title").asString());
        assertEquals("티키님에게서 편지가 도착했어요.", json.get("body").asString());
        assertEquals("LETTER", json.get("type").asString());
        assertEquals(1L, json.get("notificationId").asLong());
        assertEquals(12L, json.get("letterId").asLong());
        assertTrue(json.get("cardId").isNull());
    }

    @Test
    void 푸시_수신_설정을_끈_회원에게는_보내지_않는다() {
        Member member = member("pushoff", false);
        subscribe(member, "https://fcm.googleapis.com/fcm/send/off");

        dispatcher.dispatch(event(member));

        verify(pushSender, never()).send(anyString(), anyString(), anyString(), any());
    }

    @Test
    void 만료된_구독은_지우고_실패한_구독은_남긴다() {
        Member member = member("pushexpired", true);
        subscribe(member, "https://fcm.googleapis.com/fcm/send/expired");
        subscribe(member, "https://fcm.googleapis.com/fcm/send/failed");
        subscribe(member, "https://fcm.googleapis.com/fcm/send/error");
        when(pushSender.send(eq("https://fcm.googleapis.com/fcm/send/expired"), anyString(), anyString(), any()))
                .thenReturn(PushSendResult.EXPIRED);
        when(pushSender.send(eq("https://fcm.googleapis.com/fcm/send/failed"), anyString(), anyString(), any()))
                .thenReturn(PushSendResult.FAILED);
        when(pushSender.send(eq("https://fcm.googleapis.com/fcm/send/error"), anyString(), anyString(), any()))
                .thenThrow(new IllegalStateException("암호화 실패"));

        dispatcher.dispatch(event(member));

        List<String> remaining = pushSubscriptionRepository.findByMemberIdOrderByUpdatedAtDesc(member.getId()).stream()
                .map(PushSubscription::getEndpoint).toList();
        assertEquals(2, remaining.size());
        assertTrue(remaining.containsAll(List.of(
                "https://fcm.googleapis.com/fcm/send/failed", "https://fcm.googleapis.com/fcm/send/error")));
    }

    /** 404·410을 받기 전에 같은 브라우저가 다시 등록했으면(키 갱신·다른 회원으로 옮김) 새 구독은 지우지 않는다. */
    @Test
    void 발송하는_동안_다시_등록한_구독은_지우지_않는다() {
        Member member = member("pushrenewold", true);
        Member next = member("pushrenewnew", true);
        String endpoint = "https://fcm.googleapis.com/fcm/send/renewed";
        subscribe(member, endpoint);
        when(pushSender.send(eq(endpoint), anyString(), anyString(), any())).thenAnswer(invocation -> {
            Thread.sleep(5); // 등록 시각이 확실히 달라지게
            transactionTemplate.executeWithoutResult(status -> pushSubscriptionRepository.findByEndpoint(endpoint)
                    .orElseThrow().renew(next.getId(), P256DH, AUTH));
            return PushSendResult.EXPIRED;
        });

        dispatcher.dispatch(event(member));

        List<PushSubscription> remaining = pushSubscriptionRepository.findByMemberIdOrderByUpdatedAtDesc(next.getId());
        assertEquals(1, remaining.size());
        assertEquals(endpoint, remaining.get(0).getEndpoint());
    }

    /** 실제 알림 생성(친구 요청)부터: 커밋된 뒤 별도 스레드에서 보낸다. */
    @Test
    void 알림이_커밋되면_푸시를_보낸다() {
        Member requester = member("pushrequester", true);
        Member receiver = member("pushreceiver", true);
        subscribe(receiver, "https://fcm.googleapis.com/fcm/send/commit");
        when(pushSender.send(anyString(), anyString(), anyString(), any())).thenReturn(PushSendResult.SENT);

        transactionTemplate.executeWithoutResult(status ->
                eventPublisher.publishEvent(new FollowRequestedEvent(requester.getId(), receiver.getId())));

        verify(pushSender, timeout(5000)).send(eq("https://fcm.googleapis.com/fcm/send/commit"), eq(P256DH), eq(AUTH), any());
        List<Notification> created = transactionTemplate.execute(status -> em.createQuery(
                        "select n from Notification n where n.memberId = :id", Notification.class)
                .setParameter("id", receiver.getId()).getResultList());
        assertEquals(1, created.size());
    }

    @Test
    void 알림을_만든_트랜잭션이_롤백되면_보내지_않는다() throws Exception {
        Member requester = member("pushrbreq", true);
        Member receiver = member("pushrbrecv", true);
        subscribe(receiver, "https://fcm.googleapis.com/fcm/send/rollback");

        transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publishEvent(new FollowRequestedEvent(requester.getId(), receiver.getId()));
            status.setRollbackOnly();
        });

        Thread.sleep(300);
        verify(pushSender, never()).send(anyString(), anyString(), anyString(), any());
    }
}
