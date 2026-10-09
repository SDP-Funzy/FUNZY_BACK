package com.sdp1617.backend.global.cleanup;

import com.sdp1617.backend.archive.entity.ArchiveCategory;
import com.sdp1617.backend.auth.entity.Consent;
import com.sdp1617.backend.auth.entity.Member;
import com.sdp1617.backend.letter.dto.LetterCardRequest;
import com.sdp1617.backend.letter.dto.LetterEnvelopeRequest;
import com.sdp1617.backend.letter.entity.DesignType;
import com.sdp1617.backend.letter.entity.Letter;
import com.sdp1617.backend.letter.entity.LetterCard;
import com.sdp1617.backend.letter.service.LetterCardContentResolver;
import com.sdp1617.backend.letter.service.LetterInboxService;
import com.sdp1617.backend.letter.service.LetterWriteService;
import com.sdp1617.backend.letter.service.UnsentLetterCleaner;
import com.sdp1617.backend.notification.entity.Notification;
import com.sdp1617.backend.notification.entity.NotificationType;
import com.sdp1617.backend.notification.repository.NotificationRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 방치된 보내지 않은 편지와 오래된 알림 정리를 실제 DB로 검증한다 (#122, CI에서는 PostgreSQL). */
@SpringBootTest
@Transactional
class StaleDataCleanupIntegrationTest {

    private static final LocalDateTime NOW = LocalDateTime.now();
    private static final LocalDateTime CUTOFF = NOW.minusDays(90);

    @Autowired private EntityManager em;
    @Autowired private LetterWriteService letterWriteService;
    @Autowired private LetterInboxService letterInboxService;
    @Autowired private LetterCardContentResolver contentResolver;
    @Autowired private UnsentLetterCleaner unsentLetterCleaner;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private ApplicationContext applicationContext;

    private Member sender;
    private Member recipient;

    private Member member(String name) {
        Member member = new Member(name + "@stale.test", "encoded", name, Consent.requiredOnly());
        em.persist(member);
        return member;
    }

    private Long letterWithCard() {
        Long letterId = letterWriteService.start(sender.getId(),
                new LetterEnvelopeRequest("은우", "티키", DesignType.DesignType_A)).letterId();
        letterWriteService.addCard(sender.getId(), letterId, contentResolver.resolve(sender.getId(),
                new LetterCardRequest(ArchiveCategory.MUSIC, null, null, "내용", null, null, null)));
        return letterId;
    }

    private void lastUpdated(Long letterId, LocalDateTime updatedAt) {
        em.flush();
        em.createQuery("update Letter l set l.updatedAt = :updatedAt where l.id = :id")
                .setParameter("updatedAt", updatedAt).setParameter("id", letterId).executeUpdate();
        em.clear();
    }

    private long cardCount(Long letterId) {
        return em.createQuery("select count(c) from LetterCard c where c.letter.id = :id", Long.class)
                .setParameter("id", letterId).getSingleResult();
    }

    @BeforeEach
    void setUp() {
        sender = member("stalesender");
        recipient = member("stalerecipient");
    }

    @Test
    void 정리_작업은_기본으로_꺼져_있다() {
        // 운영 서버 .env의 CLEANUP_ENABLED=true로만 켠다. 로컬도 기본값으로 운영과 같은 S3 버킷을 쓴다
        assertTrue(applicationContext.getBeansOfType(ScheduledCleanupJob.class).isEmpty());
    }

    @Test
    void 마지막_수정_후_90일이_지난_보내지_않은_편지만_카드와_함께_지운다() {
        Long staleDraft = letterWithCard();
        Long staleCompleted = letterWithCard();
        letterWriteService.complete(sender.getId(), staleCompleted);
        Long recentDraft = letterWithCard();
        Long oldSent = letterWithCard();
        letterWriteService.complete(sender.getId(), oldSent);
        letterInboxService.send(sender.getId(), oldSent, recipient.getId());

        lastUpdated(staleDraft, CUTOFF.minusDays(1));
        lastUpdated(staleCompleted, CUTOFF.minusMinutes(1));
        lastUpdated(recentDraft, CUTOFF.plusDays(1));
        lastUpdated(oldSent, CUTOFF.minusDays(30));

        int deleted = unsentLetterCleaner.deleteNotUpdatedSince(CUTOFF);
        em.flush();
        em.clear();

        assertEquals(2, deleted);
        assertNull(em.find(Letter.class, staleDraft));
        assertNull(em.find(Letter.class, staleCompleted));
        assertEquals(0, cardCount(staleDraft));
        assertNotNull(em.find(Letter.class, recentDraft));
        assertEquals(1, cardCount(recentDraft));
        assertNotNull(em.find(Letter.class, oldSent)); // 보낸 편지는 받는 사람 것이라 지우지 않는다
    }

    @Test
    void 만든_지_30일이_지난_알림을_읽음_여부와_관계없이_지운다() {
        Notification oldRead = new Notification(sender.getId(), NotificationType.LETTER, "오래된 읽은 알림");
        oldRead.markAsRead();
        Notification oldUnread = new Notification(sender.getId(), NotificationType.LETTER, "오래된 안 읽은 알림");
        Notification recent = new Notification(sender.getId(), NotificationType.LETTER, "최근 알림");
        em.persist(oldRead);
        em.persist(oldUnread);
        em.persist(recent);
        em.flush();
        em.createQuery("update Notification n set n.createdAt = :createdAt where n.id in :ids")
                .setParameter("createdAt", NOW.minusDays(31))
                .setParameter("ids", java.util.List.of(oldRead.getId(), oldUnread.getId()))
                .executeUpdate();
        em.clear();

        int deleted = notificationRepository.deleteCreatedBefore(NOW.minusDays(30));

        assertEquals(2, deleted);
        assertEquals(1, notificationRepository.findByMemberIdOrderByCreatedAtDesc(sender.getId()).size());
    }
}
