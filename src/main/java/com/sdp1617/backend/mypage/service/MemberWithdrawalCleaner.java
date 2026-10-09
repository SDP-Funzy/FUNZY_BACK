package com.sdp1617.backend.mypage.service;

import com.sdp1617.backend.archive.entity.ArchiveCard;
import com.sdp1617.backend.archive.entity.ArchiveCardLike;
import com.sdp1617.backend.archive.repository.ArchiveCardLikeRepository;
import com.sdp1617.backend.archive.repository.ArchiveCardRepository;
import com.sdp1617.backend.auth.repository.SocialConnectionRepository;
import com.sdp1617.backend.letter.entity.Letter;
import com.sdp1617.backend.letter.entity.LetterInteractionType;
import com.sdp1617.backend.letter.entity.LetterStatus;
import com.sdp1617.backend.letter.repository.LetterInteractionRepository;
import com.sdp1617.backend.letter.repository.LetterRepository;
import com.sdp1617.backend.notification.repository.NotificationRepository;
import com.sdp1617.backend.social.repository.FollowRelationRepository;
import com.sdp1617.backend.social.repository.FollowRequestRepository;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 탈퇴 시 회원의 연관 데이터를 정리한다. 회원 행은 지우지 않고 익명화하므로({@code Member.withdraw}),
 * 여기서는 "본인만 쓰던 데이터"와 "다른 회원에게 영향을 주는 관계"만 지운다.
 *
 * - 삭제: 소셜 연결(재가입 가능하게), 친구 관계·팔로우 요청(상대의 친구 수·목록에서 빠지게),
 *         내 아카이브와 거기 달린 좋아요, 내가 누른 좋아요(좋아요 수도 감소), 내 알림, 내 찜,
 *         아직 보내지 않은(작성 중·완료) 내 편지, 내가 받은 편지(받은 편지함에서 숨김 — 보낸 사람의 보낸 편지함에는 남음)
 * - 유지("탈퇴한회원N"으로 표시): 주고받은 마음카드·봉투, 다른 사람 카드에 남긴 이모지·문구 코멘트·편지 리액션·댓글
 */
@Component
@RequiredArgsConstructor
public class MemberWithdrawalCleaner {

    private final SocialConnectionRepository socialConnectionRepository;
    private final FollowRelationRepository followRelationRepository;
    private final FollowRequestRepository followRequestRepository;
    private final ArchiveCardRepository archiveCardRepository;
    private final ArchiveCardLikeRepository archiveCardLikeRepository;
    private final NotificationRepository notificationRepository;
    private final LetterInteractionRepository letterInteractionRepository;
    private final LetterRepository letterRepository;

    /** 탈퇴 트랜잭션 안에서만 호출한다 — 회원 익명화와 함께 커밋되거나 함께 롤백돼야 한다. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void clean(Long memberId) {
        socialConnectionRepository.deleteByMember_Id(memberId);
        followRelationRepository.deleteAllByMember(memberId);
        followRequestRepository.deleteAllByMember(memberId);

        deleteMyLikes(memberId);
        deleteMyArchive(memberId);

        notificationRepository.deleteByMemberId(memberId);
        deleteMyUnsentLetters(memberId);
        letterRepository.revokeShareLinksBySender(memberId);
        letterRepository.hideAllReceivedBy(memberId, LocalDateTime.now());
        letterInteractionRepository.deleteByMemberIdAndType(memberId, LetterInteractionType.FAVORITE);
    }

    /**
     * 아직 보내지 않은 내 편지. 편지를 잠가 동시에 진행 중인 카드 추가가 끝난 뒤에 지운다.
     * 카드 사진은 정기 정리(#122)에서 지운다.
     */
    private void deleteMyUnsentLetters(Long memberId) {
        List<Letter> letters = letterRepository.findBySenderIdAndStatusInForUpdate(
                memberId, EnumSet.of(LetterStatus.DRAFT, LetterStatus.COMPLETED));
        letterRepository.deleteAll(letters);
    }

    /** 내가 다른 사람 아카이브 카드에 누른 좋아요. 좋아요 수도 함께 줄인다. */
    private void deleteMyLikes(Long memberId) {
        List<ArchiveCardLike> likes = archiveCardLikeRepository.findByMemberId(memberId);
        if (likes.isEmpty()) {
            return;
        }
        archiveCardRepository.findAllById(likes.stream().map(ArchiveCardLike::getArchiveCardId).toList())
                .forEach(ArchiveCard::decreaseLikeCount);
        archiveCardLikeRepository.deleteAll(likes);
    }

    /** 내 아카이브 카드와 거기 달린(다른 사람이 누른) 좋아요. */
    private void deleteMyArchive(Long memberId) {
        List<Long> myArchiveCardIds = archiveCardRepository.findByOwnerMemberIdOrderByCreatedAtDesc(memberId).stream()
                .map(ArchiveCard::getId)
                .toList();
        if (myArchiveCardIds.isEmpty()) {
            return;
        }
        archiveCardLikeRepository.deleteByArchiveCardIdIn(myArchiveCardIds);
        archiveCardRepository.deleteByOwnerMemberId(memberId);
    }
}
