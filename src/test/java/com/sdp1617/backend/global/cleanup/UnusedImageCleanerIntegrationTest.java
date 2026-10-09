package com.sdp1617.backend.global.cleanup;

import com.sdp1617.backend.archive.entity.ArchiveCard;
import com.sdp1617.backend.archive.entity.ArchiveCategory;
import com.sdp1617.backend.auth.entity.Consent;
import com.sdp1617.backend.auth.entity.Member;
import com.sdp1617.backend.global.s3.S3ImageService;
import com.sdp1617.backend.letter.dto.LetterEnvelopeRequest;
import com.sdp1617.backend.letter.entity.DesignType;
import com.sdp1617.backend.letter.entity.LetterCard;
import com.sdp1617.backend.letter.entity.LetterCardContent;
import com.sdp1617.backend.letter.service.LetterWriteService;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 안 쓰는 사진 정리가 실제 DB에서 쓰이는 사진(카드·프로필·아카이브)을 지우지 않는지 검증한다 (#122). S3만 가짜. */
@SpringBootTest
@Transactional
class UnusedImageCleanerIntegrationTest {

    private static final String CARD_IMAGE = "cards/9/in-card.jpg";
    private static final String ARCHIVED_ONLY = "cards/9/archived-only.jpg";
    private static final String ORPHAN_CARD_IMAGE = "cards/9/orphan.jpg";
    private static final String PROFILE_IMAGE = "profiles/9/profile.jpg";
    private static final String ORPHAN_PROFILE_IMAGE = "profiles/9/old-profile.jpg";

    @Autowired private EntityManager em;
    @Autowired private LetterWriteService letterWriteService;
    @Autowired private UnusedImageCleaner cleaner;
    @MockitoBean private S3ImageService s3ImageService;

    private Member member(String name) {
        Member member = new Member(name + "@cleanup.test", "encoded", name, Consent.requiredOnly());
        em.persist(member);
        return member;
    }

    private LetterCard addCard(Member sender, Long letterId, String imageKey, String imageUrl) {
        Long cardId = letterWriteService.addCard(sender.getId(), letterId, new LetterCardContent(
                ArchiveCategory.MUSIC, null, null, "내용", null, null, imageKey, imageUrl)).cards().getLast().cardId();
        return em.find(LetterCard.class, cardId);
    }

    @SuppressWarnings("unchecked")
    private void givenS3Images(String prefix, List<String> keys) {
        doAnswer(invocation -> {
            ((Consumer<List<String>>) invocation.getArgument(2)).accept(keys);
            return null;
        }).when(s3ImageService).forEachPageOfImagesUploadedBefore(eq(prefix), any(), any());
    }

    @Test
    void 카드_프로필_아카이브_어디에서도_쓰지_않는_사진만_지운다() {
        Member sender = member("cleanupsender");
        sender.updateProfileImage(PROFILE_IMAGE, "https://example.com/" + PROFILE_IMAGE);
        Long letterId = letterWriteService.start(sender.getId(),
                new LetterEnvelopeRequest("은우", "티키", DesignType.DesignType_A)).letterId();
        addCard(sender, letterId, CARD_IMAGE, "https://sdp-funzy.s3.ap-northeast-2.amazonaws.com/" + CARD_IMAGE);

        // 콕한 뒤 원래 카드의 사진이 바뀌어도, 아카이브는 복사한 주소로 계속 그 사진을 쓴다.
        // 이미지 기본 주소(CDN 등)가 바뀐 예전 주소여도 key를 알아본다.
        LetterCard archivedCard = addCard(sender, letterId, ARCHIVED_ONLY, "https://old-cdn.example.com/img/" + ARCHIVED_ONLY);
        em.persist(ArchiveCard.ofLetterCard(sender.getId(), archivedCard, ArchiveCategory.MUSIC));
        em.flush();
        em.createQuery("update LetterCard c set c.imageKey = null, c.imageUrl = null where c.id = :id")
                .setParameter("id", archivedCard.getId()).executeUpdate();
        em.clear();

        givenS3Images("cards/", List.of(CARD_IMAGE, ARCHIVED_ONLY, ORPHAN_CARD_IMAGE));
        givenS3Images("profiles/", List.of(PROFILE_IMAGE, ORPHAN_PROFILE_IMAGE));
        when(s3ImageService.deleteImages(anyList())).thenAnswer(invocation -> ((List<?>) invocation.getArgument(0)).size());

        int deleted = cleaner.deleteUnusedImagesUploadedBefore(Instant.now());

        assertEquals(2, deleted);
        verify(s3ImageService).deleteImages(List.of(ORPHAN_CARD_IMAGE));
        verify(s3ImageService).deleteImages(List.of(ORPHAN_PROFILE_IMAGE));
    }

    @Test
    void 사진을_쓰는_곳이_DB에_하나도_없으면_운영_버킷을_보는_빈_DB로_보고_지우지_않는다() {
        givenS3Images("cards/", List.of(ORPHAN_CARD_IMAGE));
        givenS3Images("profiles/", List.of(ORPHAN_PROFILE_IMAGE));

        assertEquals(0, cleaner.deleteUnusedImagesUploadedBefore(Instant.now()));
        verify(s3ImageService, never()).deleteImages(anyList());
        verify(s3ImageService, never()).forEachPageOfImagesUploadedBefore(any(), any(), any());
    }
}
