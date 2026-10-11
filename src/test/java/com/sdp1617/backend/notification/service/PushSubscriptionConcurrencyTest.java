package com.sdp1617.backend.notification.service;

import com.sdp1617.backend.auth.entity.Consent;
import com.sdp1617.backend.auth.entity.Member;
import com.sdp1617.backend.notification.dto.PushSubscriptionRequest;
import com.sdp1617.backend.notification.repository.PushSubscriptionRepository;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 같은 브라우저 구독이 동시에 처음 등록돼도 모두 성공하고 구독은 하나만 남는다 (#89). 실제 커밋이 필요해 테스트 트랜잭션 없이 실행한다. */
@SpringBootTest
class PushSubscriptionConcurrencyTest {

    private static final String ENDPOINT = "https://fcm.googleapis.com/fcm/send/concurrent-tabs";
    private static final int REQUESTS = 6;

    @Autowired private PushSubscriptionService pushSubscriptionService;
    @Autowired private PushSubscriptionRepository pushSubscriptionRepository;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private EntityManager em;

    private Long memberId;

    @AfterEach
    void cleanUp() {
        transactionTemplate.executeWithoutResult(status -> {
            em.createQuery("delete from PushSubscription s where s.endpoint = :endpoint")
                    .setParameter("endpoint", ENDPOINT).executeUpdate();
            if (memberId != null) {
                em.remove(em.find(Member.class, memberId));
            }
        });
    }

    @Test
    void 같은_회원이_같은_브라우저를_동시에_등록해도_모두_성공한다() throws Exception {
        memberId = transactionTemplate.execute(status -> {
            Member member = new Member("pushconc@push.test", "encoded", "pushconc", Consent.requiredOnly());
            em.persist(member);
            return member.getId();
        });
        PushSubscriptionRequest request = new PushSubscriptionRequest(ENDPOINT, new PushSubscriptionRequest.Keys(
                "BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4",
                "BTBZMqHH6r4Tts7J_aSIgg"));

        ExecutorService executor = Executors.newFixedThreadPool(REQUESTS);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> results = new ArrayList<>();
        try {
            for (int i = 0; i < REQUESTS; i++) {
                results.add(executor.submit(() -> {
                    start.await();
                    pushSubscriptionService.subscribe(memberId, request);
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> result : results) {
                result.get(); // 하나라도 실패하면 여기서 예외
            }
        } finally {
            executor.shutdownNow();
        }

        assertEquals(1, pushSubscriptionRepository.findByMemberIdOrderByUpdatedAtDesc(memberId).size());
    }
}
