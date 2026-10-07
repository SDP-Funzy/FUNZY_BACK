package com.sdp1617.backend.letter.service;

import com.sdp1617.backend.archive.entity.ArchiveCategory;
import com.sdp1617.backend.auth.entity.Consent;
import com.sdp1617.backend.auth.entity.Member;
import com.sdp1617.backend.global.error.CustomException;
import com.sdp1617.backend.global.error.ErrorCode;
import com.sdp1617.backend.letter.dto.CardBoxType;
import com.sdp1617.backend.letter.dto.CardFolderResponse;
import com.sdp1617.backend.letter.dto.CardStorageResponse;
import com.sdp1617.backend.letter.dto.CursorPageResponse;
import com.sdp1617.backend.letter.dto.LetterCardRequest;
import com.sdp1617.backend.letter.dto.LetterEnvelopeRequest;
import com.sdp1617.backend.letter.dto.LetterResponse;
import com.sdp1617.backend.letter.entity.DesignType;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 마음카드 보관함 조회 쿼리를 실제 DB로 검증한다 (#82: 주고받은 편지의 카드 기준). CI는 PostgreSQL에서 돌기 때문에,
 * H2에서는 통과하지만 PostgreSQL에서만 실패하는 쿼리(값이 없는 파라미터의 타입 추론, GROUP BY 등, #126)를 잡는다.
 */
@SpringBootTest
@Transactional
class LetterCardBoxIntegrationTest {

    @Autowired private EntityManager em;
    @Autowired private LetterCardBoxService cardBoxService;
    @Autowired private LetterWriteService letterWriteService;
    @Autowired private LetterInboxService letterInboxService;
    @Autowired private LetterCardContentResolver contentResolver;

    private Member me;
    private Member friendA;
    private Member friendB;

    private Member member(String name) {
        Member member = new Member(name + "@card-box.test", "encoded", name, Consent.requiredOnly());
        em.persist(member);
        return member;
    }

    /** sender가 recipient에게 카드 제목들로 편지 한 통을 써서 보낸다. */
    private Long sendLetter(Member sender, Member recipient, String... titles) {
        Long letterId = letterWriteService.start(sender.getId(),
                new LetterEnvelopeRequest("받는이", "보낸이", DesignType.DesignType_A)).letterId();
        for (String title : titles) {
            letterWriteService.addCard(sender.getId(), letterId, contentResolver.resolve(sender.getId(),
                    new LetterCardRequest(ArchiveCategory.MUSIC, null, title, "내용", null, null, null)));
        }
        letterWriteService.complete(sender.getId(), letterId);
        letterInboxService.send(sender.getId(), letterId, recipient.getId());
        return letterId;
    }

    private List<String> receivedTitles(LocalDate date, String keyword) {
        return cardBoxService.getCards(me.getId(), CardBoxType.RECEIVED, date, keyword, null, 20).items().stream()
                .map(CardStorageResponse::title)
                .toList();
    }

    @BeforeEach
    void setUp() {
        me = member("boxme");
        friendA = member("boxfriendA");
        friendB = member("boxfriendB");
        sendLetter(friendA, me, "첫번째 카드", "두번째 카드");
        sendLetter(friendB, me, "세번째_카드");
        em.flush();
    }

    @Test
    void 필터_없이_조회하고_커서로_다음_페이지를_이어서_조회한다() {
        CursorPageResponse<CardStorageResponse> first = cardBoxService.getCards(me.getId(), CardBoxType.RECEIVED, null, null, null, 2);
        assertEquals(2, first.items().size());
        assertTrue(first.hasNext());

        CursorPageResponse<CardStorageResponse> second =
                cardBoxService.getCards(me.getId(), CardBoxType.RECEIVED, null, null, first.nextCursor(), 2);
        assertEquals(1, second.items().size());
        assertFalse(second.hasNext());
    }

    @Test
    void 날짜와_검색어로_거르고_밑줄은_글자_그대로_찾는다() {
        assertEquals(3, receivedTitles(LocalDate.now(), null).size());
        assertEquals(0, receivedTitles(LocalDate.now().minusDays(1), null).size());
        assertEquals(List.of("두번째 카드"), receivedTitles(null, "두번째"));
        assertEquals(2, receivedTitles(null, "boxfriendA").size());
        assertEquals(List.of("세번째_카드"), receivedTitles(null, "째_"));
        // 받은함 검색은 상대방(보낸 사람) 기준이라, 내 닉네임으로는 걸리지 않는다
        assertEquals(0, receivedTitles(null, "boxme").size());
        // 보낸함 검색은 받은 사람 기준: friendA가 보낸 카드는 받은 사람(boxme) 닉네임으로 찾고, 자기 닉네임으로는 안 걸린다
        assertEquals(2, cardBoxService.getCards(friendA.getId(), CardBoxType.SENT, null, "boxme", null, 20).items().size());
        assertEquals(0, cardBoxService.getCards(friendA.getId(), CardBoxType.SENT, null, "boxfriendA", null, 20).items().size());
    }

    @Test
    void 상대방별_폴더를_커서로_이어서_조회한다() {
        CursorPageResponse<CardFolderResponse> first = cardBoxService.getFolders(me.getId(), CardBoxType.RECEIVED, null, 1);
        assertEquals(1, first.items().size());
        assertTrue(first.hasNext());

        CursorPageResponse<CardFolderResponse> second =
                cardBoxService.getFolders(me.getId(), CardBoxType.RECEIVED, first.nextCursor(), 1);
        assertEquals(1, second.items().size());
        assertFalse(second.hasNext());
        assertEquals(3, first.items().get(0).cardCount() + second.items().get(0).cardCount());
    }

    @Test
    void 월별_캘린더를_조회한다() {
        LocalDate today = LocalDate.now();
        assertEquals(1, cardBoxService.getCalendar(me.getId(), CardBoxType.RECEIVED, today.getYear(), today.getMonthValue())
                .days().size());
    }

    @Test
    void 받은_편지함에서_지운_편지의_카드는_받은함에서만_빠진다() {
        Long letterId = sendLetter(friendA, me, "지울 카드");
        letterInboxService.deleteOrHide(me.getId(), letterId);
        em.flush();

        assertFalse(receivedTitles(null, null).contains("지울 카드"));
        assertTrue(cardBoxService.getCards(friendA.getId(), CardBoxType.SENT, null, null, null, 20).items().stream()
                .anyMatch(card -> card.title().equals("지울 카드")));
    }

    @Test
    void 카드_1장은_주고받은_사람만_보고_보내기_전_카드는_보관함에_없다() {
        Long letterId = sendLetter(friendA, me, "단건");
        Long cardId = letterInboxService.getLetter(me.getId(), letterId).cards().get(0).cardId();

        assertEquals(cardId, cardBoxService.getCard(me.getId(), cardId).cardId());
        assertEquals(cardId, cardBoxService.getCard(friendA.getId(), cardId).cardId());
        assertEquals(ErrorCode.CARD_001,
                assertThrows(CustomException.class, () -> cardBoxService.getCard(friendB.getId(), cardId)).getErrorCode());

        LetterResponse draft = letterWriteService.start(friendA.getId(),
                new LetterEnvelopeRequest("받는이", "보낸이", DesignType.DesignType_A));
        Long draftCardId = letterWriteService.addCard(friendA.getId(), draft.letterId(), contentResolver.resolve(friendA.getId(),
                new LetterCardRequest(ArchiveCategory.MUSIC, null, "작성 중", "내용", null, null, null))).cards().get(0).cardId();
        assertEquals(ErrorCode.CARD_001,
                assertThrows(CustomException.class, () -> cardBoxService.getCard(friendA.getId(), draftCardId)).getErrorCode());
    }
}
