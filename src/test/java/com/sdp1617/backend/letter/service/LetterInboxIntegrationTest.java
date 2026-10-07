package com.sdp1617.backend.letter.service;

import com.sdp1617.backend.archive.entity.ArchiveCategory;
import com.sdp1617.backend.auth.entity.Consent;
import com.sdp1617.backend.auth.entity.Member;
import com.sdp1617.backend.letter.entity.DesignType;
import com.sdp1617.backend.global.error.CustomException;
import com.sdp1617.backend.global.error.ErrorCode;
import com.sdp1617.backend.letter.dto.LetterCardRequest;
import com.sdp1617.backend.letter.dto.LetterEnvelopeRequest;
import com.sdp1617.backend.letter.dto.LetterResponse;
import com.sdp1617.backend.letter.dto.ReceivedLetterResponse;
import com.sdp1617.backend.letter.entity.LetterSortType;
import com.sdp1617.backend.letter.entity.LetterStatus;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 편지 보내기와 받은·보낸 편지함을 실제 DB로 검증한다 (CI에서는 PostgreSQL). */
@SpringBootTest
@Transactional
class LetterInboxIntegrationTest {

    @Autowired
    private EntityManager em;

    @Autowired
    private LetterWriteService letterWriteService;

    @Autowired
    private LetterInboxService letterInboxService;

    @Autowired
    private LetterCardContentResolver contentResolver;

    private Member sender;
    private Member recipient;
    private Member stranger;

    private Member member(String name) {
        Member member = new Member(name + "@inbox.test", "encoded", name, Consent.requiredOnly());
        em.persist(member);
        return member;
    }

    private Long completedLetter(String fromName) {
        Long letterId = letterWriteService.start(sender.getId(),
                new LetterEnvelopeRequest("받는이", fromName, DesignType.DesignType_A)).letterId();
        letterWriteService.addCard(sender.getId(), letterId, contentResolver.resolve(sender.getId(),
                new LetterCardRequest(ArchiveCategory.MUSIC, null, null, "내용", null, null, null)));
        letterWriteService.complete(sender.getId(), letterId);
        return letterId;
    }

    private List<ReceivedLetterResponse> received(String senderName, LocalDate from, LocalDate to) {
        return letterInboxService.getReceivedLetters(recipient.getId(), LetterSortType.LATEST, senderName, from, to, 0, 20)
                .letters();
    }

    private void assertError(ErrorCode expected, Executable executable) {
        assertEquals(expected, assertThrows(CustomException.class, executable).getErrorCode());
    }

    @BeforeEach
    void setUp() {
        sender = member("inboxsender");
        recipient = member("inboxrecipient");
        stranger = member("inboxstranger");
    }

    @Test
    void 보낸_편지가_받은_편지함과_보낸_편지함에_나오고_처음_열면_읽음이_된다() {
        Long letterId = completedLetter("티키");

        LetterResponse sent = letterInboxService.send(sender.getId(), letterId, recipient.getId());
        assertEquals(LetterStatus.SENT, sent.status());
        assertEquals(recipient.getId(), sent.recipient().memberId());

        List<ReceivedLetterResponse> inbox = received(null, null, null);
        assertEquals(1, inbox.size());
        assertFalse(inbox.get(0).read());
        assertEquals(1, letterInboxService.getSentLetters(sender.getId(), 0, 20).letters().size());

        LetterResponse opened = letterInboxService.getLetter(recipient.getId(), letterId);
        assertNotNull(opened.readAt());
        em.flush();
        assertTrue(received(null, null, null).get(0).read());
        assertTrue(letterInboxService.getSentLetters(sender.getId(), 0, 20).letters().get(0).read());
    }

    @Test
    void 보낸_사람이_열어봐도_읽음이_되지_않는다() {
        Long letterId = completedLetter("티키");
        letterInboxService.send(sender.getId(), letterId, recipient.getId());

        assertEquals(null, letterInboxService.getLetter(sender.getId(), letterId).readAt());
    }

    @Test
    void 보낼_수_없는_경우() {
        Long draftId = letterWriteService.start(sender.getId(),
                new LetterEnvelopeRequest("받는이", "티키", DesignType.DesignType_A)).letterId();
        Long completedId = completedLetter("티키");

        assertError(ErrorCode.LETTER_006, () -> letterInboxService.send(sender.getId(), draftId, recipient.getId()));
        assertError(ErrorCode.LETTER_007, () -> letterInboxService.send(sender.getId(), completedId, sender.getId()));
        assertError(ErrorCode.LETTER_008, () -> letterInboxService.send(sender.getId(), completedId, -1L));
        assertError(ErrorCode.LETTER_001, () -> letterInboxService.send(stranger.getId(), completedId, recipient.getId()));

        letterInboxService.send(sender.getId(), completedId, recipient.getId());
        assertError(ErrorCode.LETTER_009, () -> letterInboxService.send(sender.getId(), completedId, stranger.getId()));
    }

    @Test
    void 탈퇴한_회원에게는_보낼_수_없다() {
        Long letterId = completedLetter("티키");
        recipient.withdraw();
        em.flush();

        assertError(ErrorCode.LETTER_008, () -> letterInboxService.send(sender.getId(), letterId, recipient.getId()));
    }

    @Test
    void 받은_편지함을_보낸_사람_이름이나_닉네임과_날짜로_거른다() {
        letterInboxService.send(sender.getId(), completedLetter("티키"), recipient.getId());
        letterInboxService.send(sender.getId(), completedLetter("다른이름"), recipient.getId());

        assertEquals(1, received("티키", null, null).size());
        assertEquals(2, received("INBOXSENDER", null, null).size());
        assertEquals(2, received(null, LocalDate.now(), LocalDate.now()).size());
        assertEquals(0, received(null, LocalDate.now().plusDays(1), null).size());
    }

    @Test
    void 받는_사람이_지우면_받은_편지함에서만_사라지고_보낸_편지함에는_남는다() {
        Long letterId = completedLetter("티키");
        letterInboxService.send(sender.getId(), letterId, recipient.getId());

        letterInboxService.deleteOrHide(recipient.getId(), letterId);
        em.flush();

        assertEquals(0, received(null, null, null).size());
        assertError(ErrorCode.LETTER_001, () -> letterInboxService.getLetter(recipient.getId(), letterId));
        assertEquals(1, letterInboxService.getSentLetters(sender.getId(), 0, 20).letters().size());
        assertEquals(letterId, letterInboxService.getLetter(sender.getId(), letterId).letterId());
    }

    @Test
    void 보낸_편지는_보낸_사람이_지울_수_없고_다른_사람은_볼_수_없다() {
        Long letterId = completedLetter("티키");
        letterInboxService.send(sender.getId(), letterId, recipient.getId());

        assertError(ErrorCode.LETTER_003, () -> letterInboxService.deleteOrHide(sender.getId(), letterId));
        assertError(ErrorCode.LETTER_001, () -> letterInboxService.getLetter(stranger.getId(), letterId));
        assertError(ErrorCode.LETTER_001, () -> letterInboxService.deleteOrHide(stranger.getId(), letterId));
    }
}
