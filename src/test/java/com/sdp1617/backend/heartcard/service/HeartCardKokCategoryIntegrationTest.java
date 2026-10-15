package com.sdp1617.backend.heartcard.service;

import com.sdp1617.backend.archive.entity.ArchiveCategory;
import com.sdp1617.backend.archive.repository.ArchiveCardRepository;
import com.sdp1617.backend.auth.entity.Consent;
import com.sdp1617.backend.auth.entity.Member;
import com.sdp1617.backend.heartcard.dto.HeartCardKokRequest;
import com.sdp1617.backend.letter.dto.LetterCardRequest;
import com.sdp1617.backend.letter.dto.LetterEnvelopeRequest;
import com.sdp1617.backend.letter.entity.DesignType;
import com.sdp1617.backend.letter.service.LetterCardContentResolver;
import com.sdp1617.backend.letter.service.LetterInboxService;
import com.sdp1617.backend.letter.service.LetterWriteService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 콕·아카이브 담기에서 카테고리를 생략하면 카드 자기 카테고리 보드에 들어간다 (LB-110, #156). */
@SpringBootTest
@Transactional
class HeartCardKokCategoryIntegrationTest {

    @Autowired private EntityManager em;
    @Autowired private LetterWriteService letterWriteService;
    @Autowired private LetterInboxService letterInboxService;
    @Autowired private LetterCardContentResolver contentResolver;
    @Autowired private HeartCardKokService kokService;
    @Autowired private ArchiveCardRepository archiveCardRepository;

    private Member recipient;
    private Long musicCardId;
    private Long customCardId;

    @BeforeEach
    void setUp() {
        Member sender = member("koksender");
        recipient = member("kokrecipient");
        Long letterId = letterWriteService.start(sender.getId(),
                new LetterEnvelopeRequest("은우", "티키", DesignType.DesignType_A)).letterId();
        musicCardId = addCard(sender, letterId, ArchiveCategory.MUSIC, null);
        customCardId = addCard(sender, letterId, ArchiveCategory.ETC, "게임");
        letterWriteService.complete(sender.getId(), letterId);
        letterInboxService.send(sender.getId(), letterId, recipient.getId());
    }

    @Test
    void 콕할_때_카테고리를_생략하면_카드의_카테고리로_저장한다() {
        kokService.updateKok(recipient.getId(), musicCardId, new HeartCardKokRequest(true, null));

        assertEquals(ArchiveCategory.MUSIC, archivedCategory(musicCardId));
    }

    @Test
    void 요청_본문_없이_콕해도_카드의_카테고리로_저장한다() {
        kokService.updateKok(recipient.getId(), musicCardId, null);

        assertEquals(ArchiveCategory.MUSIC, archivedCategory(musicCardId));
    }

    @Test
    void 콕할_때_카테고리를_보내면_그_카테고리로_저장한다() {
        kokService.updateKok(recipient.getId(), musicCardId, new HeartCardKokRequest(true, ArchiveCategory.BOOK));

        assertEquals(ArchiveCategory.BOOK, archivedCategory(musicCardId));
    }

    @Test
    void 직접_입력_카테고리_카드는_ETC로_저장한다() {
        kokService.updateKok(recipient.getId(), customCardId, new HeartCardKokRequest(true, null));

        assertEquals(ArchiveCategory.ETC, archivedCategory(customCardId));
    }

    private Member member(String name) {
        Member member = new Member(name + "@kok.test", "encoded", name, Consent.requiredOnly());
        em.persist(member);
        return member;
    }

    private Long addCard(Member sender, Long letterId, ArchiveCategory category, String customCategory) {
        return letterWriteService.addCard(sender.getId(), letterId, contentResolver.resolve(sender.getId(),
                        new LetterCardRequest(category, customCategory, "내용", null, null, null)))
                .cards().getLast().cardId();
    }

    private ArchiveCategory archivedCategory(Long letterCardId) {
        return archiveCardRepository.findByOwnerMemberIdAndLetterCardId(recipient.getId(), letterCardId)
                .orElseThrow().getCategory();
    }
}
