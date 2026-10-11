package com.sdp1617.backend.notification.controller;

import com.sdp1617.backend.auth.entity.Consent;
import com.sdp1617.backend.auth.entity.Member;
import com.sdp1617.backend.auth.jwt.JwtProvider;
import com.sdp1617.backend.auth.service.AllSessionsRevokedEvent;
import com.sdp1617.backend.notification.entity.PushSubscription;
import com.sdp1617.backend.notification.repository.PushSubscriptionRepository;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 웹 푸시 구독 등록·해제 (#89). 테스트 기본 설정은 푸시가 꺼져 있어 발송은 하지 않는다. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PushSubscriptionApiTest {

    private static final String P256DH =
            "BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4";
    private static final String AUTH = "BTBZMqHH6r4Tts7J_aSIgg";

    @Autowired private MockMvc mockMvc;
    @Autowired private EntityManager em;
    @Autowired private JwtProvider jwtProvider;
    @Autowired private PushSubscriptionRepository pushSubscriptionRepository;
    @Autowired private ApplicationEventPublisher eventPublisher;

    private Member member;
    private Member other;

    private Member member(String name) {
        Member member = new Member(name + "@pushapi.test", "encoded", name, Consent.requiredOnly());
        em.persist(member);
        return member;
    }

    private String bearer(Member member) {
        return "Bearer " + jwtProvider.createAccessToken(member.getId(), member.getSessionVersion());
    }

    private static String body(String endpoint, String p256dh, String auth) {
        return """
                {"endpoint":"%s","expirationTime":null,"keys":{"p256dh":"%s","auth":"%s"}}
                """.formatted(endpoint, p256dh, auth);
    }

    private ResultActions subscribe(Member member, String endpoint) throws Exception {
        return subscribeJson(member, body(endpoint, P256DH, AUTH));
    }

    private ResultActions subscribeJson(Member member, String json) throws Exception {
        return mockMvc.perform(put("/api/mypage/notifications/push/subscriptions")
                .header(HttpHeaders.AUTHORIZATION, bearer(member))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private List<PushSubscription> subscriptions(Member member) {
        em.flush();
        em.clear();
        return pushSubscriptionRepository.findByMemberIdOrderByUpdatedAtDesc(member.getId());
    }

    @BeforeEach
    void setUp() {
        member = member("pushapimember");
        other = member("pushapiother");
    }

    @Test
    void 푸시가_꺼져_있으면_공개키는_null이다() throws Exception {
        mockMvc.perform(get("/api/mypage/notifications/push/public-key")
                        .header(HttpHeaders.AUTHORIZATION, bearer(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.publicKey").doesNotExist());
    }

    @Test
    void 로그인하지_않으면_구독할_수_없다() throws Exception {
        mockMvc.perform(put("/api/mypage/notifications/push/subscriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("https://fcm.googleapis.com/fcm/send/anon", P256DH, AUTH)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 브라우저_구독을_등록하고_다시_보내면_갱신한다() throws Exception {
        subscribe(member, "https://fcm.googleapis.com/fcm/send/abc").andExpect(status().isOk());
        subscribe(member, "https://fcm.googleapis.com/fcm/send/abc").andExpect(status().isOk());

        List<PushSubscription> saved = subscriptions(member);
        assertEquals(1, saved.size());
        assertEquals("https://fcm.googleapis.com/fcm/send/abc", saved.get(0).getEndpoint());
        assertEquals(P256DH, saved.get(0).getP256dh());
        assertEquals(AUTH, saved.get(0).getAuth());
    }

    @Test
    void 같은_브라우저에서_다른_회원이_등록하면_그_회원의_구독으로_옮긴다() throws Exception {
        subscribe(member, "https://web.push.apple.com/QGuQyavXutnMH").andExpect(status().isOk());
        subscribe(other, "https://web.push.apple.com/QGuQyavXutnMH").andExpect(status().isOk());

        assertTrue(subscriptions(member).isEmpty());
        assertEquals(1, subscriptions(other).size());
    }

    @Test
    void 알려진_푸시_서비스의_https_주소만_받는다() throws Exception {
        for (String endpoint : List.of(
                "http://fcm.googleapis.com/fcm/send/abc",
                "https://evil.example.com/fcm/send/abc",
                "https://fcm.googleapis.com.evil.example.com/abc",
                "https://fcm.googleapis.com:8443/abc",
                "https://user@fcm.googleapis.com/abc",
                "https://169.254.169.254/latest/meta-data",
                "not a url")) {
            subscribe(member, endpoint)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("NOTIFICATION_002"));
        }
        subscribe(member, "https://updates.push.services.mozilla.com/wpush/v2/abc").andExpect(status().isOk());
        subscribe(member, "https://wns2-par02p.notify.windows.com/w/?token=abc").andExpect(status().isOk());
        assertEquals(2, subscriptions(member).size());
    }

    @Test
    void 키_형식이_틀리면_받지_않는다() throws Exception {
        String endpoint = "https://fcm.googleapis.com/fcm/send/keys";
        subscribeJson(member, body(endpoint, "not-a-key", AUTH))
                .andExpect(jsonPath("$.code").value("NOTIFICATION_002"));
        subscribeJson(member, body(endpoint, P256DH, "c2hvcnQ"))
                .andExpect(jsonPath("$.code").value("NOTIFICATION_002"));
        subscribeJson(member, "{\"endpoint\":\"" + endpoint + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_002"));
        assertTrue(subscriptions(member).isEmpty());
    }

    @Test
    void 회원당_최근_10개까지만_남긴다() throws Exception {
        for (int i = 0; i < 11; i++) {
            subscribe(member, "https://fcm.googleapis.com/fcm/send/device-" + i).andExpect(status().isOk());
        }

        List<String> endpoints = subscriptions(member).stream().map(PushSubscription::getEndpoint).toList();
        assertEquals(10, endpoints.size());
        assertTrue(endpoints.contains("https://fcm.googleapis.com/fcm/send/device-10"));
        assertTrue(!endpoints.contains("https://fcm.googleapis.com/fcm/send/device-0"));
    }

    @Test
    void 구독을_해제하면_지워지고_다른_회원의_구독은_지울_수_없다() throws Exception {
        subscribe(member, "https://fcm.googleapis.com/fcm/send/mine").andExpect(status().isOk());

        mockMvc.perform(delete("/api/mypage/notifications/push/subscriptions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(other))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"endpoint\":\"https://fcm.googleapis.com/fcm/send/mine\"}"))
                .andExpect(status().isOk());
        assertEquals(1, subscriptions(member).size());

        mockMvc.perform(delete("/api/mypage/notifications/push/subscriptions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"endpoint\":\"https://fcm.googleapis.com/fcm/send/mine\"}"))
                .andExpect(status().isOk());
        assertTrue(subscriptions(member).isEmpty());
    }

    @Test
    void 모든_기기에서_로그아웃되면_구독을_모두_지운다() throws Exception {
        subscribe(member, "https://fcm.googleapis.com/fcm/send/pc").andExpect(status().isOk());
        subscribe(member, "https://web.push.apple.com/phone").andExpect(status().isOk());
        subscribe(other, "https://fcm.googleapis.com/fcm/send/other").andExpect(status().isOk());

        eventPublisher.publishEvent(new AllSessionsRevokedEvent(member.getId()));

        assertTrue(subscriptions(member).isEmpty());
        assertEquals(1, subscriptions(other).size());
    }
}
