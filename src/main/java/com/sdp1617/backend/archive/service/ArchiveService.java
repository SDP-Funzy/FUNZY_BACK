package com.sdp1617.backend.archive.service;

import com.sdp1617.backend.archive.dto.ArchiveCardDetailResponse;
import com.sdp1617.backend.archive.dto.ArchiveCardResponse;
import com.sdp1617.backend.archive.dto.ArchiveCategorySectionResponse;
import com.sdp1617.backend.archive.dto.ArchiveHomeResponse;
import com.sdp1617.backend.archive.entity.ArchiveCard;
import com.sdp1617.backend.archive.entity.ArchiveCategory;
import com.sdp1617.backend.archive.repository.ArchiveCardCommentRepository;
import com.sdp1617.backend.archive.repository.ArchiveCardRepository;
import com.sdp1617.backend.auth.entity.Member;
import com.sdp1617.backend.auth.repository.MemberRepository;
import com.sdp1617.backend.global.error.CustomException;
import com.sdp1617.backend.global.error.ErrorCode;
import com.sdp1617.backend.social.repository.FollowRelationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ArchiveService {

    private static final String ARCHIVE_TITLE = "아카이브";

    private final ArchiveCardRepository archiveCardRepository;
    private final ArchiveCardCommentRepository archiveCardCommentRepository;
    private final ArchiveCardAccess archiveCardAccess;
    private final FollowRelationRepository followRelationRepository;
    private final MemberRepository memberRepository;

    public ArchiveHomeResponse getHome(Long memberId) {
        return buildHome(findActiveMember(memberId));
    }

    /**
     * 친구(맞팔)의 아카이브 홈. 탭 구조는 내 아카이브와 같고, 비공개 카드는 내 아카이브와 마찬가지로 나오지 않는다.
     * 친구가 아니면(탈퇴로 관계가 정리된 경우 포함) SOCIAL_007.
     */
    public ArchiveHomeResponse getFriendHome(Long viewerMemberId, Long friendMemberId) {
        if (followRelationRepository.findBetween(viewerMemberId, friendMemberId).isEmpty()) {
            throw new CustomException(ErrorCode.SOCIAL_007);
        }
        return buildHome(findActiveMember(friendMemberId));
    }

    /** 아카이브에는 공개 카드만 보인다. 비공개(잠금) 카드는 주인에게도 편지 보관함의 잠긴편지 필터로만 보인다 (#147). */
    private ArchiveHomeResponse buildHome(Member owner) {
        List<ArchiveCategorySectionResponse> sections = Arrays.stream(ArchiveCategory.values())
                .map(category -> ArchiveCategorySectionResponse.of(
                        category,
                        archiveCardRepository.findByOwnerMemberIdAndCategoryAndLockedFalseOrderByCreatedAtDesc(owner.getId(), category)
                                .stream()
                                .map(ArchiveCardResponse::from)
                                .toList()
                ))
                .toList();
        boolean empty = sections.stream().allMatch(ArchiveCategorySectionResponse::empty);

        return new ArchiveHomeResponse(
                owner.getId(), owner.getNickname(), ARCHIVE_TITLE, owner.getProfileImageUrl(), empty, sections);
    }

    private Member findActiveMember(Long memberId) {
        return memberRepository.findActiveById(memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.AUTH_002));
    }

    /**
     * 주인은 공개·비공개 카드 모두, 친구(맞팔)는 공개 카드만 본다.
     * 친구가 아니거나 비공개 카드면 카드가 없는 것과 같은 응답을 준다 — 순번 ID로 남의 카드 존재 여부를 알아낼 수 없게.
     */
    public ArchiveCardDetailResponse getCard(Long viewerMemberId, Long archiveCardId) {
        ArchiveCard card = archiveCardAccess.requireViewable(viewerMemberId, archiveCardId, ErrorCode.ARCHIVE_002);
        return ArchiveCardDetailResponse.from(card, archiveCardCommentRepository.countByArchiveCard_Id(archiveCardId));
    }
}
