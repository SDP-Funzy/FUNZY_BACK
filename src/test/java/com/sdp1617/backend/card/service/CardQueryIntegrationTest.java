package com.sdp1617.backend.card.service;

import com.sdp1617.backend.archive.entity.ArchiveCategory;
import com.sdp1617.backend.auth.entity.Consent;
import com.sdp1617.backend.auth.entity.Member;
import com.sdp1617.backend.card.dto.CardBoxType;
import com.sdp1617.backend.card.dto.DesignType;
import com.sdp1617.backend.card.dto.response.CardFolderResponse;
import com.sdp1617.backend.card.dto.response.CardStorageResponse;
import com.sdp1617.backend.card.dto.response.CursorPageResponse;
import com.sdp1617.backend.card.entity.Card;
import com.sdp1617.backend.card.entity.Envelop;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 마음카드 보관함 조회 쿼리를 실제 DB로 검증한다. CI는 PostgreSQL에서 돌기 때문에,
 * H2에서는 통과하지만 PostgreSQL에서만 실패하는 쿼리(값이 없는 파라미터의 타입 추론, timestamp 범위 등)를 잡는다.
 */
@SpringBootTest
@Transactional
class CardQueryIntegrationTest {

    @Autowired
    private EntityManager em;

    @Autowired
    private CardService cardService;

    private Member me;

    private Member member(String name) {
        Member member = new Member(name + "@card-query.test", "encoded", name, Consent.requiredOnly());
        em.persist(member);
        return member;
    }

    private void card(Member sender, Member receiver, String title) {
        Envelop envelop = em.createQuery(
                        "select e from Envelop e where e.sender = :s and e.receiver = :r", Envelop.class)
                .setParameter("s", sender).setParameter("r", receiver)
                .getResultStream().findFirst()
                .orElseGet(() -> {
                    Envelop created = Envelop.create(sender, receiver, DesignType.values()[0]);
                    em.persist(created);
                    return created;
                });
        em.persist(Card.create(envelop, title, ArchiveCategory.values()[0], null, null, "내용"));
    }

    @BeforeEach
    void setUp() {
        me = member("cardme");
        Member friendA = member("cardfriendA");
        Member friendB = member("cardfriendB");
        card(friendA, me, "첫번째 카드");
        card(friendA, me, "두번째 카드");
        card(friendB, me, "세번째 카드");
        em.flush();
    }

    @Test
    void 필터_없이_조회하고_커서로_다음_페이지를_이어서_조회한다() {
        CursorPageResponse<CardStorageResponse> first = cardService.getCards(me.getId(), CardBoxType.RECEIVED, null, null, null, 2);
        assertEquals(2, first.items().size());
        assertTrue(first.hasNext());

        CursorPageResponse<CardStorageResponse> second =
                cardService.getCards(me.getId(), CardBoxType.RECEIVED, null, null, first.nextCursor(), 2);
        assertEquals(1, second.items().size());
        assertFalse(second.hasNext());
    }

    @Test
    void 날짜와_검색어로_거른다() {
        assertEquals(3, cardService.getCards(me.getId(), CardBoxType.RECEIVED, LocalDate.now(), null, null, 20).items().size());
        assertEquals(0, cardService.getCards(me.getId(), CardBoxType.RECEIVED, LocalDate.now().minusDays(1), null, null, 20).items().size());
        assertEquals(1, cardService.getCards(me.getId(), CardBoxType.RECEIVED, null, "두번째", null, 20).items().size());
        assertEquals(2, cardService.getCards(me.getId(), CardBoxType.RECEIVED, null, "cardfriendA", null, 20).items().size());
    }

    @Test
    void 상대방별_폴더를_커서로_이어서_조회한다() {
        CursorPageResponse<CardFolderResponse> first = cardService.getFolders(me.getId(), CardBoxType.RECEIVED, null, 1);
        assertEquals(1, first.items().size());
        assertTrue(first.hasNext());

        CursorPageResponse<CardFolderResponse> second =
                cardService.getFolders(me.getId(), CardBoxType.RECEIVED, first.nextCursor(), 1);
        assertEquals(1, second.items().size());
        assertFalse(second.hasNext());
    }

    @Test
    void 월별_캘린더를_조회한다() {
        LocalDate today = LocalDate.now();
        assertEquals(1, cardService.getCalendar(me.getId(), CardBoxType.RECEIVED, today.getYear(), today.getMonthValue())
                .days().size());
    }
}
