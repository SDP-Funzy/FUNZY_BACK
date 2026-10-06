package com.sdp1617.backend.archive.service;

import com.sdp1617.backend.archive.dto.ArchiveCardCreateRequest;
import com.sdp1617.backend.archive.dto.ArchiveCardDetailResponse;
import com.sdp1617.backend.archive.dto.ArchiveCardResponse;
import com.sdp1617.backend.archive.dto.ArchiveCategorySectionResponse;
import com.sdp1617.backend.archive.dto.ArchiveHomeResponse;
import com.sdp1617.backend.archive.dto.ArchiveLikeResponse;
import com.sdp1617.backend.archive.dto.ArchiveVisibilityResponse;
import com.sdp1617.backend.archive.dto.ArchiveVisibilityUpdateRequest;
import com.sdp1617.backend.archive.entity.ArchiveCard;
import com.sdp1617.backend.archive.entity.ArchiveCategory;
import com.sdp1617.backend.archive.entity.ArchiveVisibility;
import com.sdp1617.backend.archive.entity.ArchiveCardLike;
import com.sdp1617.backend.archive.repository.ArchiveCardLikeRepository;
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
    private final ArchiveCardLikeRepository archiveCardLikeRepository;
    private final FollowRelationRepository followRelationRepository;
    private final MemberRepository memberRepository;

    public ArchiveHomeResponse getHome(Long memberId) {
        return buildHome(findActiveMember(memberId), false);
    }

    /**
     * 친구(맞팔)의 아카이브 홈. 탭 구조는 내 아카이브와 같고, 친구가 비공개로 설정한 이미지·메시지는 가려서 내린다.
     * 친구가 아니면(탈퇴로 관계가 정리된 경우 포함) SOCIAL_007.
     */
    public ArchiveHomeResponse getFriendHome(Long viewerMemberId, Long friendMemberId) {
        if (followRelationRepository.findBetween(viewerMemberId, friendMemberId).isEmpty()) {
            throw new CustomException(ErrorCode.SOCIAL_007);
        }
        return buildHome(findActiveMember(friendMemberId), true);
    }

    private ArchiveHomeResponse buildHome(Member owner, boolean maskPrivateFields) {
        List<ArchiveCategorySectionResponse> sections = Arrays.stream(ArchiveCategory.values())
                .map(category -> ArchiveCategorySectionResponse.of(
                        category,
                        archiveCardRepository.findByOwnerMemberIdAndCategoryOrderByCreatedAtDesc(owner.getId(), category)
                                .stream()
                                .map(card -> ArchiveCardResponse.from(card, maskPrivateFields))
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

    @Transactional
    public ArchiveCardDetailResponse saveCard(Long memberId, ArchiveCardCreateRequest request) {
        if (archiveCardRepository.existsByOwnerMemberIdAndLetterCardId(memberId, request.letterCardId())) {
            throw new CustomException(ErrorCode.ARCHIVE_001);
        }

        ArchiveCard card = archiveCardRepository.save(new ArchiveCard(memberId, request.letterCardId(), request.category()));
        return ArchiveCardDetailResponse.from(card, false, false);
    }

    /**
     * 주인은 전체 항목을, 친구(맞팔)는 공개 항목만 본다. 마스킹 여부는 클라이언트가 고르지 않고 서버가 정한다.
     * 친구가 아니면 카드가 없는 것과 같은 응답을 준다 — 순번 ID로 남의 카드 존재 여부를 알아낼 수 없게.
     */
    public ArchiveCardDetailResponse getCard(Long viewerMemberId, Long archiveCardId) {
        ArchiveCard card = findCard(archiveCardId);
        boolean owner = card.isOwnedBy(viewerMemberId);
        if (!owner && !isFriend(viewerMemberId, card)) {
            throw new CustomException(ErrorCode.ARCHIVE_002);
        }
        boolean maskPrivateFields = !owner;
        boolean liked = archiveCardLikeRepository.existsByArchiveCardIdAndMemberId(archiveCardId, viewerMemberId);
        return ArchiveCardDetailResponse.from(card, maskPrivateFields, liked);
    }

    @Transactional
    public void deleteCard(Long memberId, Long archiveCardId) {
        ArchiveCard card = archiveCardRepository.findByIdAndOwnerMemberId(archiveCardId, memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.ARCHIVE_002));
        archiveCardLikeRepository.deleteByArchiveCardId(archiveCardId);
        archiveCardRepository.delete(card);
    }

    @Transactional
    public ArchiveVisibilityResponse updateVisibility(
            Long memberId,
            Long archiveCardId,
            ArchiveVisibilityUpdateRequest request
    ) {
        ArchiveCard card = archiveCardRepository.findByIdAndOwnerMemberId(archiveCardId, memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.ARCHIVE_002));
        card.updateVisibility(new ArchiveVisibility(
                request.senderVisible(),
                request.receiverVisible(),
                request.dateVisible(),
                request.imageVisible(),
                request.messageVisible()
        ));
        return ArchiveVisibilityResponse.from(card);
    }

    @Transactional
    public ArchiveLikeResponse toggleLike(Long memberId, Long archiveCardId) {
        ArchiveCard card = findCard(archiveCardId);
        if (card.isOwnedBy(memberId)) {
            throw new CustomException(ErrorCode.ARCHIVE_003);
        }

        // 이미 누른 좋아요는 친구를 끊은 뒤에도 취소할 수 있게 하고, 새 좋아요만 친구에게 허용한다
        return archiveCardLikeRepository.findByArchiveCardIdAndMemberId(archiveCardId, memberId)
                .map(like -> unlike(card, like))
                .orElseGet(() -> {
                    if (!isFriend(memberId, card)) {
                        throw new CustomException(ErrorCode.ARCHIVE_002);
                    }
                    return like(card, memberId);
                });
    }

    private boolean isFriend(Long memberId, ArchiveCard card) {
        return followRelationRepository.findBetween(memberId, card.getOwnerMemberId()).isPresent();
    }

    private ArchiveLikeResponse like(ArchiveCard card, Long memberId) {
        archiveCardLikeRepository.save(new ArchiveCardLike(card.getId(), memberId));
        card.increaseLikeCount();
        return new ArchiveLikeResponse(card.getId(), card.getLikeCount(), true);
    }

    private ArchiveLikeResponse unlike(ArchiveCard card, ArchiveCardLike like) {
        archiveCardLikeRepository.delete(like);
        card.decreaseLikeCount();
        return new ArchiveLikeResponse(card.getId(), card.getLikeCount(), false);
    }

    private ArchiveCard findCard(Long archiveCardId) {
        return archiveCardRepository.findById(archiveCardId)
                .orElseThrow(() -> new CustomException(ErrorCode.ARCHIVE_002));
    }
}
