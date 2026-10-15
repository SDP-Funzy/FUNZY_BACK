package com.sdp1617.backend.archive.service;

import com.sdp1617.backend.archive.entity.ArchiveCard;
import com.sdp1617.backend.archive.repository.ArchiveCardRepository;
import com.sdp1617.backend.global.error.CustomException;
import com.sdp1617.backend.global.error.ErrorCode;
import com.sdp1617.backend.social.repository.FollowRelationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 아카이브 카드를 볼 수 있는지 한 곳에서 판단한다 (카드 상세·댓글 공통).
 * 주인은 공개·비공개 카드 모두, 친구(맞팔)는 공개 카드만 본다 (#147).
 */
@Component
@RequiredArgsConstructor
public class ArchiveCardAccess {

    private final ArchiveCardRepository archiveCardRepository;
    private final FollowRelationRepository followRelationRepository;

    /** 볼 수 없으면 카드가 없는 것과 같이 notFound — 순번 ID로 남의 카드 존재 여부를 알아낼 수 없게. */
    public ArchiveCard requireViewable(Long viewerMemberId, Long archiveCardId, ErrorCode notFound) {
        ArchiveCard card = archiveCardRepository.findById(archiveCardId)
                .orElseThrow(() -> new CustomException(notFound));
        if (!canView(viewerMemberId, card)) {
            throw new CustomException(notFound);
        }
        return card;
    }

    public boolean canView(Long viewerMemberId, ArchiveCard card) {
        if (card.isOwnedBy(viewerMemberId)) {
            return true;
        }
        return !card.isLocked()
                && followRelationRepository.findBetween(viewerMemberId, card.getOwnerMemberId()).isPresent();
    }
}
