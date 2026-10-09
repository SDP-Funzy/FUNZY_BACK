package com.sdp1617.backend.letter.service;

import com.sdp1617.backend.archive.entity.ArchiveCategory;
import com.sdp1617.backend.auth.entity.Consent;
import com.sdp1617.backend.auth.entity.Member;
import com.sdp1617.backend.global.error.CustomException;
import com.sdp1617.backend.global.error.ErrorCode;
import com.sdp1617.backend.letter.dto.LetterCardRequest;
import com.sdp1617.backend.letter.dto.LetterEnvelopeRequest;
import com.sdp1617.backend.letter.dto.ShareLinkResponse;
import com.sdp1617.backend.letter.dto.SharedLetterResponse;
import com.sdp1617.backend.letter.dto.SharedLetterResponse.Viewer;
import com.sdp1617.backend.letter.entity.DesignType;
import com.sdp1617.backend.letter.entity.LetterSortType;
import com.sdp1617.backend.letter.entity.LetterStatus;
import com.sdp1617.backend.mypage.service.AccountSettingsService;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 공유 링크로 보내기(#87)와 링크로 받은 편지의 받는 사람 확정(#114)을 실제 DB로 검증한다. */
@SpringBootTest
@Transactional
class LetterShareIntegrationTest {

    @Autowired private EntityManager em;
    @Autowired private LetterWriteService letterWriteService;
    @Autowired private LetterInboxService letterInboxService;
    @Autowired private LetterCardContentResolver contentResolver;
    @Autowired private LetterShareService letterShareService;
    @Autowired private LetterCardBoxService letterCardBoxService;
    @Autowired private AccountSettingsService accountSettingsService;

    private Member sender;
    private Member first;
    private Member second;

    private Member member(String name) {
        Member member = new Member(name + "@share.test", "encoded", name, Consent.requiredOnly());
        em.persist(member);
        return member;
    }

    private Long draftLetter() {
        Long letterId = letterWriteService.start(sender.getId(),
                new LetterEnvelopeRequest("은우", "티키", DesignType.DesignType_A)).letterId();
        letterWriteService.addCard(sender.getId(), letterId, contentResolver.resolve(sender.getId(),
                new LetterCardRequest(ArchiveCategory.MUSIC, null, "내용", null, null, null)));
        return letterId;
    }

    private Long completedLetter() {
        Long letterId = draftLetter();
        letterWriteService.complete(sender.getId(), letterId);
        return letterId;
    }

    private void assertError(ErrorCode expected, Executable executable) {
        assertEquals(expected, assertThrows(CustomException.class, executable).getErrorCode());
    }

    @BeforeEach
    void setUp() {
        sender = member("sharesender");
        first = member("sharefirst");
        second = member("sharesecond");
    }

    @Test
    void 링크를_만들면_보낸_편지가_되고_누구나_읽기_전용으로_본다() {
        Long letterId = completedLetter();

        ShareLinkResponse link = letterShareService.issue(sender.getId(), letterId);

        assertTrue(link.expiresAt().isAfter(LocalDateTime.now().plusDays(29)));
        SharedLetterResponse anonymous = letterShareService.view(link.token(), null);
        assertEquals(LetterStatus.SENT, anonymous.letter().status());
        assertNull(anonymous.letter().recipient());
        assertEquals(Viewer.ANONYMOUS, anonymous.viewer());
        assertFalse(anonymous.receivable());
        assertEquals(Viewer.SENDER, letterShareService.view(link.token(), sender.getId()).viewer());
        assertFalse(letterShareService.view(link.token(), sender.getId()).receivable());
        SharedLetterResponse loggedIn = letterShareService.view(link.token(), first.getId());
        assertEquals(Viewer.OTHER, loggedIn.viewer());
        assertTrue(loggedIn.receivable());
        // 보기만 해서는 받는 사람이 정해지지 않는다
        assertNull(letterShareService.view(link.token(), null).letter().recipient());
        // 링크로 보낸 편지는 더 이상 수정할 수 없다
        assertError(ErrorCode.LETTER_003, () -> letterWriteService.updateEnvelope(sender.getId(), letterId,
                new LetterEnvelopeRequest("수정", "티키", DesignType.DesignType_A)));
    }

    @Test
    void 처음_받은_회원이_받는_사람이_되고_다른_회원은_읽기만_한다() {
        Long letterId = completedLetter();
        String token = letterShareService.issue(sender.getId(), letterId).token();

        letterShareService.receive(token, first.getId());
        em.flush();

        assertEquals(first.getId(), letterShareService.view(token, first.getId()).letter().recipient().memberId());
        assertEquals(1, letterInboxService.getReceivedLetters(first.getId(), LetterSortType.LATEST, null, null, null, 0, 20)
                .letters().size());
        assertEquals(first.getId(), letterInboxService.getLetter(first.getId(), letterId).recipient().memberId());
        assertEquals(Viewer.RECIPIENT, letterShareService.view(token, first.getId()).viewer());
        // 같은 회원이 다시 받으면 그대로 성공, 다른 회원은 LETTER_012 (읽기는 계속 가능)
        assertEquals(first.getId(), letterShareService.receive(token, first.getId()).recipient().memberId());
        assertError(ErrorCode.LETTER_012, () -> letterShareService.receive(token, second.getId()));
        SharedLetterResponse other = letterShareService.view(token, second.getId());
        assertEquals(Viewer.OTHER, other.viewer());
        assertFalse(other.receivable());
        // 보낸 사람 본인은 받을 수 없다
        assertError(ErrorCode.LETTER_013, () -> letterShareService.receive(token, sender.getId()));
    }

    @Test
    void 다시_발급하면_이전_링크는_무효이고_취소하면_열_수_없지만_받은_사람은_계속_본다() {
        Long letterId = completedLetter();
        String oldToken = letterShareService.issue(sender.getId(), letterId).token();
        letterShareService.receive(oldToken, first.getId());

        String newToken = letterShareService.issue(sender.getId(), letterId).token();
        assertNotEquals(oldToken, newToken);
        assertError(ErrorCode.LETTER_011, () -> letterShareService.view(oldToken, null));
        assertEquals(first.getId(), letterShareService.view(newToken, sender.getId()).letter().recipient().memberId());

        letterShareService.revoke(sender.getId(), letterId);
        assertError(ErrorCode.LETTER_011, () -> letterShareService.view(newToken, null));
        assertError(ErrorCode.LETTER_011, () -> letterShareService.receive(newToken, second.getId()));
        assertEquals(letterId, letterInboxService.getLetter(first.getId(), letterId).letterId());
    }

    @Test
    void 만료된_링크는_열_수도_받을_수도_없다() {
        Long letterId = completedLetter();
        String token = letterShareService.issue(sender.getId(), letterId).token();
        em.flush();
        em.createQuery("update Letter l set l.shareTokenExpiresAt = :expiredAt where l.id = :id")
                .setParameter("expiredAt", LocalDateTime.now().minusSeconds(1)).setParameter("id", letterId)
                .executeUpdate();
        em.clear();

        assertError(ErrorCode.LETTER_011, () -> letterShareService.view(token, null));
        assertError(ErrorCode.LETTER_011, () -> letterShareService.receive(token, first.getId()));
        assertError(ErrorCode.LETTER_011, () -> letterShareService.view("없는토큰", null));
    }

    @Test
    void 직접_보내기와_같이_쓴다() {
        // 아직 아무도 받지 않은 링크 편지를 회원에게 직접 보내면 그 회원이 받는 사람
        Long linkFirst = completedLetter();
        String token = letterShareService.issue(sender.getId(), linkFirst).token();
        letterInboxService.send(sender.getId(), linkFirst, first.getId());
        assertEquals(first.getId(), letterShareService.view(token, first.getId()).letter().recipient().memberId());
        assertError(ErrorCode.LETTER_012, () -> letterShareService.receive(token, second.getId()));

        // 직접 보낸 편지도 링크를 만들 수 있고, 받는 사람은 그대로
        Long directFirst = completedLetter();
        letterInboxService.send(sender.getId(), directFirst, first.getId());
        String directToken = letterShareService.issue(sender.getId(), directFirst).token();
        assertEquals(Viewer.RECIPIENT, letterShareService.view(directToken, first.getId()).viewer());
        assertError(ErrorCode.LETTER_012, () -> letterShareService.receive(directToken, second.getId()));

        // 링크로 이미 받은 편지는 다른 회원에게 직접 보낼 수 없다
        Long received = completedLetter();
        String receivedToken = letterShareService.issue(sender.getId(), received).token();
        letterShareService.receive(receivedToken, first.getId());
        assertError(ErrorCode.LETTER_009, () -> letterInboxService.send(sender.getId(), received, second.getId()));
    }

    @Test
    void 링크로_받은_편지의_받은_날짜는_받기를_누른_시각이다() {
        Long letterId = completedLetter();
        String token = letterShareService.issue(sender.getId(), letterId).token();
        em.flush();
        // 링크는 한 달 전에 만들어졌다
        em.createQuery("update Letter l set l.sentAt = :issuedAt where l.id = :id")
                .setParameter("issuedAt", LocalDateTime.now().minusDays(30)).setParameter("id", letterId)
                .executeUpdate();
        em.clear();

        letterShareService.receive(token, first.getId());
        em.flush();
        em.clear();

        LocalDate today = LocalDate.now();
        var received = letterInboxService.getReceivedLetters(first.getId(), LetterSortType.LATEST, null, today, today, 0, 20)
                .letters();
        assertEquals(1, received.size());
        assertTrue(received.get(0).receivedAt().isAfter(LocalDateTime.now().minusMinutes(1)));
    }

    @Test
    void 보낸_사람이_탈퇴하면_공유_링크도_취소된다() {
        Long letterId = completedLetter();
        String token = letterShareService.issue(sender.getId(), letterId).token();
        em.flush();

        accountSettingsService.withdraw(sender.getId());
        em.flush();
        em.clear();

        assertError(ErrorCode.LETTER_011, () -> letterShareService.view(token, null));
        assertError(ErrorCode.LETTER_011, () -> letterShareService.receive(token, first.getId()));
    }

    @Test
    void 아무도_받지_않은_링크_편지는_보낸_사람이_지울_수_있고_받은_뒤에는_지울_수_없다() {
        Long unreceived = completedLetter();
        String token = letterShareService.issue(sender.getId(), unreceived).token();
        letterShareService.revoke(sender.getId(), unreceived);

        letterInboxService.deleteOrHide(sender.getId(), unreceived);
        em.flush();

        assertError(ErrorCode.LETTER_001, () -> letterInboxService.getLetter(sender.getId(), unreceived));
        assertError(ErrorCode.LETTER_011, () -> letterShareService.view(token, null));

        // 링크가 살아 있어도 아직 아무도 받지 않았으면 지울 수 있고, 링크도 함께 무효
        Long stillLinked = completedLetter();
        String liveToken = letterShareService.issue(sender.getId(), stillLinked).token();
        letterInboxService.deleteOrHide(sender.getId(), stillLinked);
        em.flush();
        assertError(ErrorCode.LETTER_011, () -> letterShareService.receive(liveToken, first.getId()));

        Long received = completedLetter();
        String receivedToken = letterShareService.issue(sender.getId(), received).token();
        letterShareService.receive(receivedToken, first.getId());
        assertError(ErrorCode.LETTER_003, () -> letterInboxService.deleteOrHide(sender.getId(), received));
    }

    @Test
    void 작성_중인_편지와_남의_편지는_링크를_만들_수_없다() {
        Long draft = draftLetter();
        Long completed = completedLetter();

        assertError(ErrorCode.LETTER_006, () -> letterShareService.issue(sender.getId(), draft));
        assertError(ErrorCode.LETTER_001, () -> letterShareService.issue(first.getId(), completed));
        assertError(ErrorCode.LETTER_001, () -> letterShareService.revoke(first.getId(), completed));
    }

    @Test
    void 아직_받는_사람이_없는_링크_편지는_보낸_편지함에_받는_사람_없이_나온다() {
        Long letterId = completedLetter();
        letterShareService.issue(sender.getId(), letterId);
        em.flush();

        var sent = letterInboxService.getSentLetters(sender.getId(), 0, 20).letters();
        assertEquals(1, sent.size());
        assertNull(sent.get(0).recipient());
    }

    @Test
    void 보낸_사람_받는_사람이_아니면_받는_사람_정보와_읽음_선물을_가린다() {
        Long letterId = completedLetter();
        String token = letterShareService.issue(sender.getId(), letterId).token();
        letterShareService.receive(token, first.getId());
        letterInboxService.getLetter(first.getId(), letterId); // 읽음
        em.flush();

        for (Long viewerId : new Long[]{null, second.getId()}) {
            var masked = letterShareService.view(token, viewerId).letter();
            assertNull(masked.recipient());
            assertNull(masked.readAt());
            assertNull(masked.selectedGiftItemId());
            assertEquals(1, masked.cards().size());
        }
        var forSender = letterShareService.view(token, sender.getId()).letter();
        assertEquals(first.getId(), forSender.recipient().memberId());
        assertTrue(forSender.readAt() != null);
    }

    @Test
    void 받은_편지함에서_지운_받는_사람은_링크로_다시_받아_되돌린다() {
        Long letterId = completedLetter();
        String token = letterShareService.issue(sender.getId(), letterId).token();
        letterShareService.receive(token, first.getId());
        letterInboxService.deleteOrHide(first.getId(), letterId);
        em.flush();

        SharedLetterResponse hidden = letterShareService.view(token, first.getId());
        assertEquals(Viewer.RECIPIENT, hidden.viewer());
        assertTrue(hidden.receivable());
        assertError(ErrorCode.LETTER_001, () -> letterInboxService.getLetter(first.getId(), letterId));

        letterShareService.receive(token, first.getId());
        em.flush();

        assertEquals(letterId, letterInboxService.getLetter(first.getId(), letterId).letterId());
        assertFalse(letterShareService.view(token, first.getId()).receivable());
    }

    @Test
    void 아직_받는_사람이_없는_링크_편지의_카드는_보관함에서_열리지_않고_받으면_열린다() {
        Long letterId = completedLetter();
        String token = letterShareService.issue(sender.getId(), letterId).token();
        Long cardId = letterInboxService.getLetter(sender.getId(), letterId).cards().get(0).cardId();
        em.flush();

        // 보관함 목록에도 없는 편지라 카드 1장 조회도 없는 카드로 응답 (받는 사람 정보가 없어 500이 나던 문제)
        assertError(ErrorCode.CARD_001, () -> letterCardBoxService.getCard(sender.getId(), cardId));

        letterShareService.receive(token, first.getId());
        em.flush();
        assertEquals(first.getId(), letterCardBoxService.getCard(sender.getId(), cardId).receiverId());
        assertEquals(cardId, letterCardBoxService.getCard(first.getId(), cardId).cardId());
    }
}
