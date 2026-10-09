package com.sdp1617.backend.letter.service;

import com.sdp1617.backend.archive.entity.ArchiveCategory;
import com.sdp1617.backend.auth.entity.Consent;
import com.sdp1617.backend.auth.entity.Member;
import com.sdp1617.backend.global.error.CustomException;
import com.sdp1617.backend.global.error.ErrorCode;
import com.sdp1617.backend.letter.dto.LetterCardRequest;
import com.sdp1617.backend.letter.dto.LetterEnvelopeRequest;
import com.sdp1617.backend.letter.entity.DesignType;
import com.sdp1617.backend.letter.entity.Letter;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 같은 링크로 동시에 받으면 1명만 받는 사람이 되고, 나중 요청이 먼저 받은 회원을 덮어쓰지 않는다 (#114).
 * 실제로 두 트랜잭션이 겹쳐야 하므로 테스트 트랜잭션 없이 커밋하고, 끝나면 직접 지운다.
 */
@SpringBootTest
class LetterShareConcurrencyTest {

    @Autowired private EntityManager em;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private LetterWriteService letterWriteService;
    @Autowired private LetterCardContentResolver contentResolver;
    @Autowired private LetterShareService letterShareService;

    private final List<Long> memberIds = new ArrayList<>();
    private Long letterId;

    private Long member(String name) {
        Long id = transactionTemplate.execute(status -> {
            Member member = new Member(name + "@concurrent.test", "encoded", name, Consent.requiredOnly());
            em.persist(member);
            return member.getId();
        });
        memberIds.add(id);
        return id;
    }

    @AfterEach
    void cleanUp() {
        transactionTemplate.executeWithoutResult(status -> {
            if (letterId != null) {
                Letter letter = em.find(Letter.class, letterId);
                if (letter != null) {
                    em.remove(letter);
                }
            }
            em.flush();
            memberIds.forEach(id -> em.remove(em.find(Member.class, id)));
        });
    }

    @Test
    void 동시에_받아도_1명만_받는_사람이_된다() throws Exception {
        Long senderId = member("concsender");
        Long a = member("concreceivera");
        Long b = member("concreceiverb");
        letterId = transactionTemplate.execute(status -> {
            Long id = letterWriteService.start(senderId,
                    new LetterEnvelopeRequest("은우", "티키", DesignType.DesignType_A)).letterId();
            letterWriteService.addCard(senderId, id, contentResolver.resolve(senderId,
                    new LetterCardRequest(ArchiveCategory.MUSIC, null, "내용", null, null, null)));
            letterWriteService.complete(senderId, id);
            return id;
        });
        String token = letterShareService.issue(senderId, letterId).token();

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Future<Long>> results = new ArrayList<>();
        for (Long receiverId : List.of(a, b)) {
            results.add(pool.submit(() -> {
                start.await();
                try {
                    letterShareService.receive(token, receiverId);
                    return receiverId;
                } catch (CustomException e) {
                    assertEquals(ErrorCode.LETTER_012, e.getErrorCode());
                    return null;
                }
            }));
        }
        start.countDown();
        List<Long> winners = new ArrayList<>();
        for (Future<Long> result : results) {
            Long winner = result.get(30, TimeUnit.SECONDS);
            if (winner != null) {
                winners.add(winner);
            }
        }
        pool.shutdown();

        assertEquals(1, winners.size());
        Long recipientId = transactionTemplate.execute(status -> em.find(Letter.class, letterId).getRecipient().getId());
        assertEquals(winners.get(0), recipientId);
    }
}
