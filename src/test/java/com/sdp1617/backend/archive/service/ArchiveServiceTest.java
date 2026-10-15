package com.sdp1617.backend.archive.service;

import com.sdp1617.backend.archive.dto.ArchiveCardDetailResponse;
import com.sdp1617.backend.archive.entity.ArchiveCard;
import com.sdp1617.backend.archive.entity.ArchiveCategory;
import com.sdp1617.backend.archive.repository.ArchiveCardRepository;
import com.sdp1617.backend.archive.dto.ArchiveCardResponse;
import com.sdp1617.backend.archive.dto.ArchiveHomeResponse;
import com.sdp1617.backend.auth.entity.Consent;
import com.sdp1617.backend.auth.entity.Member;
import com.sdp1617.backend.auth.repository.MemberRepository;
import com.sdp1617.backend.letter.service.ReceivedLetterAccess;
import com.sdp1617.backend.global.error.CustomException;
import com.sdp1617.backend.global.error.ErrorCode;
import com.sdp1617.backend.social.entity.FollowRelation;
import com.sdp1617.backend.social.repository.FollowRelationRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArchiveServiceTest {

    private static final Long CARD_ID = 10L;
    private static final Long OWNER_ID = 1L;
    private static final Long FRIEND_ID = 2L;
    private static final Long STRANGER_ID = 3L;

    @Mock
    private ArchiveCardRepository archiveCardRepository;


    @Mock
    private FollowRelationRepository followRelationRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private ReceivedLetterAccess receivedLetterAccess;

    @InjectMocks
    private ArchiveService archiveService;

    private ArchiveCard card;

    @BeforeEach
    void setUp() {
        card = new ArchiveCard(OWNER_ID, 100L, ArchiveCategory.values()[0]);
        ReflectionTestUtils.setField(card, "id", CARD_ID);
        ReflectionTestUtils.setField(card, "message", "카드 메시지");
        ReflectionTestUtils.setField(card, "senderName", "보낸 사람");
        ReflectionTestUtils.setField(card, "imageUrl", "https://example.test/card-image.jpg");
        lenient().when(archiveCardRepository.findById(CARD_ID)).thenReturn(Optional.of(card));
        lenient().when(followRelationRepository.findBetween(FRIEND_ID, OWNER_ID))
                .thenReturn(Optional.of(FollowRelation.of(FRIEND_ID, OWNER_ID)));
        lenient().when(followRelationRepository.findBetween(STRANGER_ID, OWNER_ID)).thenReturn(Optional.empty());
    }

    @Test
    void 주인은_비공개_카드도_상세를_본다() {
        card.updateLocked(true);

        ArchiveCardDetailResponse response = archiveService.getCard(OWNER_ID, CARD_ID);

        assertEquals("카드 메시지", response.message());
        assertTrue(response.locked());
    }

    @Test
    void 친구는_공개_카드의_모든_항목을_본다() {
        ArchiveCardDetailResponse response = archiveService.getCard(FRIEND_ID, CARD_ID);

        assertEquals("카드 메시지", response.message());
        assertEquals("https://example.test/card-image.jpg", response.imageUrl());
        assertEquals("보낸 사람", response.senderName());
    }

    @Test
    void 친구는_비공개_카드를_볼_수_없고_없는_카드와_같은_에러를_받는다() {
        card.updateLocked(true);

        CustomException exception = assertThrows(CustomException.class,
                () -> archiveService.getCard(FRIEND_ID, CARD_ID));

        assertEquals(ErrorCode.ARCHIVE_002, exception.getErrorCode());
    }

    @Test
    void 친구가_아니면_카드가_없는_것과_같은_에러를_받는다() {
        CustomException exception = assertThrows(CustomException.class,
                () -> archiveService.getCard(STRANGER_ID, CARD_ID));

        assertEquals(ErrorCode.ARCHIVE_002, exception.getErrorCode());
    }

    private Member owner() {
        Member owner = new Member("owner@archive.test", "encoded", "카드주인", Consent.requiredOnly());
        ReflectionTestUtils.setField(owner, "id", OWNER_ID);
        return owner;
    }

    private ArchiveCardResponse onlyCard(ArchiveHomeResponse home) {
        return home.sections().stream().flatMap(section -> section.cards().stream()).findFirst().orElseThrow();
    }

    private void stubOwnerArchive() {
        when(memberRepository.findActiveById(OWNER_ID)).thenReturn(Optional.of(owner()));
        when(archiveCardRepository.findByOwnerMemberIdAndCategoryAndLockedFalseOrderByCreatedAtDesc(eq(OWNER_ID), any()))
                .thenReturn(List.of());
        when(archiveCardRepository.findByOwnerMemberIdAndCategoryAndLockedFalseOrderByCreatedAtDesc(OWNER_ID, card.getCategory()))
                .thenReturn(List.of(card));
    }

    @Test
    void 내_아카이브_홈은_공개_카드와_닉네임을_내린다() {
        stubOwnerArchive();

        ArchiveHomeResponse home = archiveService.getHome(OWNER_ID);

        assertEquals("카드주인", home.nickname());
        assertEquals("카드 메시지", onlyCard(home).messagePreview());
    }

    @Test
    void 친구_아카이브_홈은_공개_카드를_그대로_보여준다() {
        stubOwnerArchive();

        ArchiveHomeResponse home = archiveService.getFriendHome(FRIEND_ID, OWNER_ID);

        assertEquals(OWNER_ID, home.memberId());
        assertEquals("카드 메시지", onlyCard(home).messagePreview());
        assertEquals("https://example.test/card-image.jpg", onlyCard(home).imageUrl());
    }

    @Test
    void 친구가_아니면_친구_아카이브_홈을_볼_수_없다() {
        CustomException exception = assertThrows(CustomException.class,
                () -> archiveService.getFriendHome(STRANGER_ID, OWNER_ID));

        assertEquals(ErrorCode.SOCIAL_007, exception.getErrorCode());
        verify(archiveCardRepository, never()).findByOwnerMemberIdAndCategoryAndLockedFalseOrderByCreatedAtDesc(any(), any());
    }
}
