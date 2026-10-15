package com.sdp1617.backend.letter.service;

import com.sdp1617.backend.archive.dto.ArchiveCardCreateRequest;
import com.sdp1617.backend.archive.entity.ArchiveCard;
import com.sdp1617.backend.archive.entity.ArchiveCategory;
import com.sdp1617.backend.archive.service.ArchiveService;
import com.sdp1617.backend.auth.entity.Consent;
import com.sdp1617.backend.auth.entity.Member;
import com.sdp1617.backend.letter.entity.DesignType;
import com.sdp1617.backend.global.error.CustomException;
import com.sdp1617.backend.global.error.ErrorCode;
import com.sdp1617.backend.heartcard.dto.HeartCardKokRequest;
import com.sdp1617.backend.heartcard.dto.HeartCardKokResponse;
import com.sdp1617.backend.heartcard.service.HeartCardKokService;
import com.sdp1617.backend.letter.dto.DoodlePickRequest;
import com.sdp1617.backend.letter.dto.LetterCardRequest;
import com.sdp1617.backend.letter.dto.LetterEnvelopeRequest;
import com.sdp1617.backend.letter.dto.LetterCardKokResponse;
import com.sdp1617.backend.letter.dto.LetterResponse;
import com.sdp1617.backend.letter.dto.ReceivedLetterResponse;
import com.sdp1617.backend.letter.entity.LetterSortType;
import jakarta.persistence.EntityManager;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 받은 편지의 콕(공개/비공개)과 선물 고르기, 받은 편지 삭제 시 아카이브 카드 정리를 실제 DB로 검증한다 (#82 3단계, #147, #163). */
@SpringBootTest
@Transactional
class ReceivedLetterKokGiftIntegrationTest {

    private static final String CONTENT = "출근길마다 듣는 노래야";

    @Autowired private EntityManager em;
    @Autowired private LetterWriteService letterWriteService;
    @Autowired private LetterInboxService letterInboxService;
    @Autowired private LetterCardContentResolver contentResolver;
    @Autowired private HeartCardKokService kokService;
    @Autowired private ArchiveService archiveService;

    private Member sender;
    private Member recipient;
    private Member stranger;
    private Long letterId;
    private Long cardId;
    private List<Long> giftItemIds;

    private Member member(String name) {
        Member member = new Member(name + "@reaction.test", "encoded", name, Consent.requiredOnly());
        em.persist(member);
        return member;
    }

    private long count(String jpql) {
        return em.createQuery(jpql, Long.class).getSingleResult();
    }

    private void assertError(ErrorCode expected, Executable executable) {
        assertEquals(expected, assertThrows(CustomException.class, executable).getErrorCode());
    }

    @BeforeEach
    void setUp() {
        sender = member("reactsender");
        recipient = member("reactrecipient");
        stranger = member("reactstranger");
        letterId = letterWriteService.start(sender.getId(),
                new LetterEnvelopeRequest("은우", "티키", DesignType.DesignType_A)).letterId();
        cardId = letterWriteService.addCard(sender.getId(), letterId, contentResolver.resolve(sender.getId(),
                new LetterCardRequest(ArchiveCategory.MUSIC, null, CONTENT, null, null, null))).cards().get(0).cardId();
        LetterResponse withPick = letterWriteService.replaceDoodlePick(sender.getId(), letterId,
                new DoodlePickRequest("이유", List.of("향초", "목베개")));
        giftItemIds = withPick.doodlePick().giftItems().stream().map(item -> item.giftItemId()).toList();
        letterWriteService.complete(sender.getId(), letterId);
        letterInboxService.send(sender.getId(), letterId, recipient.getId());
    }

    @Test
    void 받은_사람만_콕할_수_있다() {
        assertError(ErrorCode.COMMON_001, () ->
                kokService.updateKok(sender.getId(), cardId, new HeartCardKokRequest(true, ArchiveCategory.MUSIC)));
        assertError(ErrorCode.COMMON_001, () ->
                kokService.updateKok(stranger.getId(), cardId, new HeartCardKokRequest(true, ArchiveCategory.MUSIC)));
        assertError(ErrorCode.COMMON_001, () -> kokService.getKok(stranger.getId(), cardId));
    }

    @Test
    void 콕하면_카드_내용이_아카이브에_담기고_남의_카드는_담을_수_없다() {
        kokService.updateKok(recipient.getId(), cardId, new HeartCardKokRequest(true, ArchiveCategory.MUSIC));

        ArchiveCard archived = em.createQuery("select a from ArchiveCard a where a.ownerMemberId = :id", ArchiveCard.class)
                .setParameter("id", recipient.getId()).getSingleResult();
        assertEquals(CONTENT, archived.getMessage());
        assertEquals("티키", archived.getSenderName());
        assertEquals("은우", archived.getReceiverName());

        assertError(ErrorCode.COMMON_001, () ->
                archiveService.saveCard(stranger.getId(), new ArchiveCardCreateRequest(cardId, ArchiveCategory.ETC)));
    }

    @Test
    void 선물을_고르고_바꾸고_취소할_수_있다() {
        assertEquals(giftItemIds.get(0),
                letterInboxService.selectGift(recipient.getId(), letterId, giftItemIds.get(0)).selectedGiftItemId());
        assertEquals(giftItemIds.get(1),
                letterInboxService.selectGift(recipient.getId(), letterId, giftItemIds.get(1)).selectedGiftItemId());
        assertNull(letterInboxService.selectGift(recipient.getId(), letterId, null).selectedGiftItemId());

        assertError(ErrorCode.LETTER_010, () -> letterInboxService.selectGift(recipient.getId(), letterId, -1L));
        assertError(ErrorCode.COMMON_001, () -> letterInboxService.selectGift(sender.getId(), letterId, giftItemIds.get(0)));
    }

    @Test
    void 받은_편지를_지우면_콕한_아카이브_카드가_지워지고_더_콕할_수_없다() {
        kokService.updateKok(recipient.getId(), cardId, new HeartCardKokRequest(true, ArchiveCategory.MUSIC));
        letterInboxService.selectGift(recipient.getId(), letterId, giftItemIds.get(0));
        em.flush();

        letterInboxService.deleteOrHide(recipient.getId(), letterId);
        em.flush();

        assertEquals(0, count("select count(a) from ArchiveCard a"));
        // 보낸 사람의 편지와 고른 선물 기록은 남는다
        assertEquals(giftItemIds.get(0), letterInboxService.getLetter(sender.getId(), letterId).selectedGiftItemId());

        assertError(ErrorCode.COMMON_001, () ->
                kokService.updateKok(recipient.getId(), cardId, new HeartCardKokRequest(true, ArchiveCategory.MUSIC)));
    }

    @Test
    void 비공개로_콕하면_아카이브에_안_나오고_잠긴편지_필터에만_나온다() {
        HeartCardKokResponse kok = kokService.updateKok(recipient.getId(), cardId,
                new HeartCardKokRequest(true, null, true));
        em.flush();

        assertTrue(kok.locked());
        assertTrue(archiveService.getHome(recipient.getId()).empty());
        LetterCardKokResponse cardKok = letterInboxService.getLetter(recipient.getId(), letterId).cards().get(0).kok();
        assertEquals(kok.archiveCardId(), cardKok.archiveCardId());
        assertTrue(cardKok.locked());

        List<ReceivedLetterResponse> locked = receivedLetters(true);
        assertEquals(List.of(letterId), locked.stream().map(ReceivedLetterResponse::letterId).toList());
        assertTrue(locked.get(0).locked());
    }

    @Test
    void 이미_콕한_카드는_locked만_보내_공개와_비공개를_바꿀_수_있다() {
        kokService.updateKok(recipient.getId(), cardId, new HeartCardKokRequest(true, ArchiveCategory.MUSIC));
        em.flush();
        assertFalse(archiveService.getHome(recipient.getId()).empty());
        assertTrue(receivedLetters(true).isEmpty());
        assertFalse(receivedLetters(false).get(0).locked());

        kokService.updateKok(recipient.getId(), cardId, new HeartCardKokRequest(true, null, true));
        em.flush();
        assertTrue(archiveService.getHome(recipient.getId()).empty());
        assertEquals(1, receivedLetters(true).size());

        // locked를 생략하면 그대로 둔다
        assertTrue(kokService.updateKok(recipient.getId(), cardId, new HeartCardKokRequest(true, null)).locked());

        kokService.updateKok(recipient.getId(), cardId, new HeartCardKokRequest(true, null, false));
        em.flush();
        assertFalse(archiveService.getHome(recipient.getId()).empty());
        assertTrue(receivedLetters(true).isEmpty());
    }

    @Test
    void 콕_상태는_받는_사람이_볼_때만_카드에_붙는다() {
        kokService.updateKok(recipient.getId(), cardId, new HeartCardKokRequest(true, ArchiveCategory.MUSIC));
        em.flush();

        assertNotNull(letterInboxService.getLetter(recipient.getId(), letterId).cards().get(0).kok());
        assertNull(letterInboxService.getLetter(sender.getId(), letterId).cards().get(0).kok());
    }

    private List<ReceivedLetterResponse> receivedLetters(boolean lockedOnly) {
        return letterInboxService.getReceivedLetters(recipient.getId(), LetterSortType.LATEST, null, null, null,
                lockedOnly, 0, 20).letters();
    }
}
