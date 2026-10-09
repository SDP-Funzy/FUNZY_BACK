package com.sdp1617.backend.letter.service;

import com.sdp1617.backend.archive.entity.ArchiveCategory;
import com.sdp1617.backend.auth.entity.Consent;
import com.sdp1617.backend.auth.entity.Member;
import com.sdp1617.backend.letter.entity.DesignType;
import com.sdp1617.backend.global.error.CustomException;
import com.sdp1617.backend.global.error.ErrorCode;
import com.sdp1617.backend.letter.dto.DoodlePickRequest;
import com.sdp1617.backend.letter.dto.LetterCardRequest;
import com.sdp1617.backend.letter.dto.LetterEnvelopeRequest;
import com.sdp1617.backend.letter.dto.LetterResponse;
import com.sdp1617.backend.letter.entity.Letter;
import com.sdp1617.backend.letter.entity.LetterCardContent;
import com.sdp1617.backend.letter.entity.LetterStatus;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 편지 쓰기 흐름을 실제 DB로 검증한다 (CI에서는 PostgreSQL). */
@SpringBootTest
@Transactional
class LetterWriteIntegrationTest {

    @Autowired
    private EntityManager em;

    @Autowired
    private LetterWriteService letterWriteService;

    @Autowired
    private LetterCardContentResolver contentResolver;

    @Autowired
    private LetterInboxService letterInboxService;

    private Long writerId;
    private Long otherId;

    private Long member(String name) {
        Member member = new Member(name + "@letter.test", "encoded", name, Consent.requiredOnly());
        em.persist(member);
        return member.getId();
    }

    private LetterCardContent card(String content) {
        return content(new LetterCardRequest(ArchiveCategory.MUSIC, null, content, null, null, null));
    }

    /** 사진 없는 카드라 S3를 호출하지 않는다. */
    private LetterCardContent content(LetterCardRequest request) {
        return contentResolver.resolve(writerId, request);
    }

    private LetterResponse newLetter() {
        return letterWriteService.start(writerId, new LetterEnvelopeRequest("은우", "티키", DesignType.DesignType_A));
    }

    private void assertError(ErrorCode expected, Executable executable) {
        assertEquals(expected, assertThrows(CustomException.class, executable).getErrorCode());
    }

    @BeforeEach
    void setUp() {
        writerId = member("letterwriter");
        otherId = member("letterother");
    }

    @Test
    void 봉투_카드_두들픽을_작성하고_완료한다() {
        Long letterId = newLetter().letterId();

        letterWriteService.addCard(writerId, letterId, card("첫 카드"));
        letterWriteService.addCard(writerId, letterId, content(new LetterCardRequest(
                ArchiveCategory.ETC, "게임", "둘째 카드", "https://game.example.com", "게임 링크", null)));
        letterWriteService.replaceDoodlePick(writerId, letterId, new DoodlePickRequest("요즘 피곤해 보여서", List.of("향초", "목베개")));
        LetterResponse completed = letterWriteService.complete(writerId, letterId);

        assertEquals(LetterStatus.COMPLETED, completed.status());
        assertEquals(List.of(1, 2), completed.cards().stream().map(c -> c.cardOrder()).toList());
        assertEquals("게임", completed.cards().get(1).customCategory());
        assertEquals(List.of("향초", "목베개"),
                completed.doodlePick().giftItems().stream().map(item -> item.name()).toList());

        em.flush();
        em.clear();
        LetterResponse reloaded = letterInboxService.getLetter(writerId, letterId);
        assertEquals(2, reloaded.cards().size());
        assertEquals("요즘 피곤해 보여서", reloaded.doodlePick().reason());
    }

    @Test
    void 기타가_아닌_카테고리는_직접_입력값을_저장하지_않는다() {
        Long letterId = newLetter().letterId();

        LetterResponse response = letterWriteService.addCard(writerId, letterId,
                content(new LetterCardRequest(ArchiveCategory.MUSIC, "무시됨", "내용", null, null, null)));

        assertNull(response.cards().get(0).customCategory());
    }

    @Test
    void 카드는_5장까지만_추가된다() {
        Long letterId = newLetter().letterId();
        for (int i = 1; i <= Letter.MAX_CARD_COUNT; i++) {
            letterWriteService.addCard(writerId, letterId, card("카드" + i));
        }

        assertError(ErrorCode.LETTER_002, () -> letterWriteService.addCard(writerId, letterId, card("여섯째")));
    }

    @Test
    void 카드를_지우면_순서를_다시_매기고_마지막_카드를_지우면_작성_중으로_돌아간다() {
        Long letterId = newLetter().letterId();
        Long first = letterWriteService.addCard(writerId, letterId, card("1")).cards().get(0).cardId();
        letterWriteService.addCard(writerId, letterId, card("2"));
        letterWriteService.complete(writerId, letterId);

        LetterResponse afterFirstRemoved = letterWriteService.removeCard(writerId, letterId, first);
        assertEquals(1, afterFirstRemoved.cards().get(0).cardOrder());
        assertEquals("2", afterFirstRemoved.cards().get(0).content());

        LetterResponse empty = letterWriteService.removeCard(writerId, letterId, afterFirstRemoved.cards().get(0).cardId());
        assertEquals(LetterStatus.DRAFT, empty.status());
        assertNull(empty.completedAt());
    }

    @Test
    void 카드가_없으면_완료할_수_없다() {
        Long letterId = newLetter().letterId();

        assertError(ErrorCode.LETTER_004, () -> letterWriteService.complete(writerId, letterId));
    }

    @Test
    void 다른_사람의_편지는_없는_편지와_같이_응답한다() {
        Long letterId = newLetter().letterId();

        assertError(ErrorCode.LETTER_001, () -> letterInboxService.getLetter(otherId, letterId));
        assertError(ErrorCode.LETTER_001, () -> letterWriteService.addCard(otherId, letterId, card("끼어들기")));
        assertError(ErrorCode.LETTER_001, () -> letterInboxService.deleteOrHide(otherId, letterId));
    }

    @Test
    void 전송된_편지는_수정하거나_삭제할_수_없다() {
        Long letterId = newLetter().letterId();
        Long cardId = letterWriteService.addCard(writerId, letterId, card("1")).cards().get(0).cardId();
        ReflectionTestUtils.setField(em.find(Letter.class, letterId), "status", LetterStatus.SENT);
        em.flush();

        assertError(ErrorCode.LETTER_003, () -> letterWriteService.addCard(writerId, letterId, card("2")));
        assertError(ErrorCode.LETTER_003, () -> letterWriteService.updateCard(writerId, letterId, cardId, card("수정")));
        assertError(ErrorCode.LETTER_003, () -> letterWriteService.removeDoodlePick(writerId, letterId));
        assertError(ErrorCode.LETTER_003, () -> letterInboxService.deleteOrHide(writerId, letterId));
    }

    @Test
    void 보내지_않은_편지만_이어쓰기_목록에_나오고_삭제할_수_있다() {
        Long draft = newLetter().letterId();
        Long sent = newLetter().letterId();
        ReflectionTestUtils.setField(em.find(Letter.class, sent), "status", LetterStatus.SENT);
        em.flush();

        assertEquals(List.of(draft),
                letterWriteService.getUnsentLetters(writerId).stream().map(letter -> letter.letterId()).toList());

        letterInboxService.deleteOrHide(writerId, draft);
        em.flush();
        assertEquals(0, letterWriteService.getUnsentLetters(writerId).size());
    }
}
