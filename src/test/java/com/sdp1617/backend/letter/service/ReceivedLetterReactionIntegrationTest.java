package com.sdp1617.backend.letter.service;

import com.sdp1617.backend.archive.dto.ArchiveCardCreateRequest;
import com.sdp1617.backend.archive.entity.ArchiveCard;
import com.sdp1617.backend.archive.entity.ArchiveCardLike;
import com.sdp1617.backend.archive.entity.ArchiveCategory;
import com.sdp1617.backend.archive.service.ArchiveService;
import com.sdp1617.backend.auth.entity.Consent;
import com.sdp1617.backend.auth.entity.Member;
import com.sdp1617.backend.letter.entity.DesignType;
import com.sdp1617.backend.giftitem.dto.GiftItemEmojiRequest;
import com.sdp1617.backend.giftitem.service.GiftItemEmojiService;
import com.sdp1617.backend.global.error.CustomException;
import com.sdp1617.backend.global.error.ErrorCode;
import com.sdp1617.backend.heartcard.dto.HeartCardEmojiRequest;
import com.sdp1617.backend.heartcard.dto.HeartCardKokRequest;
import com.sdp1617.backend.heartcard.dto.HeartCardPhraseCommentCreateRequest;
import com.sdp1617.backend.heartcard.entity.HeartCardEmojiType;
import com.sdp1617.backend.heartcard.service.HeartCardEmojiService;
import com.sdp1617.backend.heartcard.service.HeartCardKokService;
import com.sdp1617.backend.heartcard.service.HeartCardPhraseCommentService;
import com.sdp1617.backend.letter.dto.DoodlePickRequest;
import com.sdp1617.backend.letter.dto.LetterCardRequest;
import com.sdp1617.backend.letter.dto.LetterEnvelopeRequest;
import com.sdp1617.backend.letter.dto.LetterResponse;
import com.sdp1617.backend.letter.entity.LetterReactionType;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 받은 편지에 다는 반응과 선물 고르기, 받은 편지 삭제 시 반응 정리를 실제 DB로 검증한다 (#82 3단계). */
@SpringBootTest
@Transactional
class ReceivedLetterReactionIntegrationTest {

    private static final String CONTENT = "출근길마다 듣는 노래야";

    @Autowired private EntityManager em;
    @Autowired private LetterWriteService letterWriteService;
    @Autowired private LetterInboxService letterInboxService;
    @Autowired private LetterCardContentResolver contentResolver;
    @Autowired private HeartCardEmojiService heartCardEmojiService;
    @Autowired private HeartCardPhraseCommentService phraseCommentService;
    @Autowired private HeartCardKokService kokService;
    @Autowired private GiftItemEmojiService giftItemEmojiService;
    @Autowired private LetterInteractionService letterInteractionService;
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
                new LetterCardRequest(ArchiveCategory.MUSIC, null, null, CONTENT, null, null, null))).cards().get(0).cardId();
        LetterResponse withPick = letterWriteService.replaceDoodlePick(sender.getId(), letterId,
                new DoodlePickRequest("이유", List.of("향초", "목베개")));
        giftItemIds = withPick.doodlePick().giftItems().stream().map(item -> item.giftItemId()).toList();
        letterWriteService.complete(sender.getId(), letterId);
        letterInboxService.send(sender.getId(), letterId, recipient.getId());
    }

    @Test
    void 받은_사람만_카드에_반응할_수_있다() {
        heartCardEmojiService.updateEmoji(recipient.getId(), cardId, new HeartCardEmojiRequest(HeartCardEmojiType.HEART));
        assertEquals(HeartCardEmojiType.HEART, heartCardEmojiService.getMyEmoji(recipient.getId(), cardId).emoji());

        assertError(ErrorCode.COMMON_001, () ->
                heartCardEmojiService.updateEmoji(sender.getId(), cardId, new HeartCardEmojiRequest(HeartCardEmojiType.HEART)));
        assertError(ErrorCode.COMMON_001, () ->
                heartCardEmojiService.updateEmoji(stranger.getId(), cardId, new HeartCardEmojiRequest(HeartCardEmojiType.HEART)));
        assertError(ErrorCode.COMMON_001, () -> heartCardEmojiService.getMyEmoji(stranger.getId(), cardId));
    }

    @Test
    void 문구_코멘트는_카드_본문_기준으로_검증하고_보낸_사람도_볼_수_있다() {
        phraseCommentService.createComment(recipient.getId(), cardId,
                new HeartCardPhraseCommentCreateRequest(0, 3, "출근길", "나도!"));

        assertError(ErrorCode.COMMON_002, () -> phraseCommentService.createComment(recipient.getId(), cardId,
                new HeartCardPhraseCommentCreateRequest(4, 6, "틀림", "본문과 다름")));
        assertEquals(1, phraseCommentService.getComments(sender.getId(), cardId).comments().size());
        assertError(ErrorCode.COMMON_001, () -> phraseCommentService.getComments(stranger.getId(), cardId));
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
    void 받은_편지를_지우면_내_반응이_모두_지워지고_더_반응할_수_없다() {
        heartCardEmojiService.updateEmoji(recipient.getId(), cardId, new HeartCardEmojiRequest(HeartCardEmojiType.HEART));
        phraseCommentService.createComment(recipient.getId(), cardId,
                new HeartCardPhraseCommentCreateRequest(0, 3, "출근길", "나도!"));
        kokService.updateKok(recipient.getId(), cardId, new HeartCardKokRequest(true, ArchiveCategory.MUSIC));
        Long archiveCardId = em.createQuery("select a.id from ArchiveCard a where a.ownerMemberId = :id", Long.class)
                .setParameter("id", recipient.getId()).getSingleResult();
        em.persist(new ArchiveCardLike(archiveCardId, stranger.getId()));
        giftItemEmojiService.updateEmoji(recipient.getId(), giftItemIds.get(0), new GiftItemEmojiRequest(HeartCardEmojiType.LIKE));
        letterInteractionService.saveReaction(recipient.getId(), letterId, LetterReactionType.HEART);
        letterInteractionService.saveFavorite(recipient.getId(), letterId);
        letterInboxService.selectGift(recipient.getId(), letterId, giftItemIds.get(0));
        em.flush();

        letterInboxService.deleteOrHide(recipient.getId(), letterId);
        em.flush();

        assertEquals(0, count("select count(e) from HeartCardEmojiReaction e"));
        assertEquals(0, count("select count(c) from HeartCardPhraseComment c"));
        assertEquals(0, count("select count(a) from ArchiveCard a"));
        assertEquals(0, count("select count(l) from ArchiveCardLike l"));
        assertEquals(0, count("select count(g) from GiftItemEmojiReaction g"));
        assertEquals(0, count("select count(i) from LetterInteraction i"));
        // 보낸 사람의 편지와 고른 선물 기록은 남는다
        assertEquals(giftItemIds.get(0), letterInboxService.getLetter(sender.getId(), letterId).selectedGiftItemId());

        assertError(ErrorCode.COMMON_001, () ->
                heartCardEmojiService.updateEmoji(recipient.getId(), cardId, new HeartCardEmojiRequest(HeartCardEmojiType.HEART)));
        assertError(ErrorCode.COMMON_001, () ->
                letterInteractionService.saveFavorite(recipient.getId(), letterId));
    }
}
