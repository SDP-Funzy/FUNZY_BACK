package com.sdp1617.backend.archive.service;

import com.sdp1617.backend.archive.dto.ArchiveCommentCreateRequest;
import com.sdp1617.backend.archive.dto.ArchiveCommentPageResponse;
import com.sdp1617.backend.archive.dto.ArchiveCommentResponse;
import com.sdp1617.backend.archive.dto.ArchiveCommentUpdateRequest;
import com.sdp1617.backend.archive.entity.ArchiveCard;
import com.sdp1617.backend.archive.entity.ArchiveCardComment;
import com.sdp1617.backend.archive.repository.ArchiveCardCommentRepository;
import com.sdp1617.backend.auth.entity.Member;
import com.sdp1617.backend.auth.repository.MemberRepository;
import com.sdp1617.backend.global.error.CustomException;
import com.sdp1617.backend.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 아카이브 카드 댓글 (#164, 인스타식). 카드 주인과 주인의 친구가 쓰고 본다. 비공개(잠금) 카드는 주인만 (#147).
 * - 답글은 한 단계만: 답글에 답하면 같은 원댓글 아래로 모은다. 원댓글을 지우면 답글도 지운다.
 * - 수정은 작성자만(아직 볼 수 있을 때), 삭제는 작성자 또는 카드 주인. 내 댓글은 카드를 볼 수 없게 된 뒤에도 지울 수 있다.
 * - 볼 수 없는 카드·댓글은 존재 여부를 드러내지 않도록 없는 것과 같이 응답한다 (ARCHIVE_002·ARCHIVE_004).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ArchiveCommentService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final Sort OLDEST_FIRST = Sort.by(Sort.Direction.ASC, "createdAt", "id");

    private final ArchiveCardCommentRepository commentRepository;
    private final ArchiveCardAccess archiveCardAccess;
    private final MemberRepository memberRepository;
    private final ApplicationEventPublisher eventPublisher;

    /** 원댓글 목록 (오래된 순). 원댓글마다 답글 수를 함께 내린다. */
    public ArchiveCommentPageResponse getComments(Long viewerMemberId, Long archiveCardId, int page, int size) {
        ArchiveCard card = requireReadableCard(viewerMemberId, archiveCardId);
        Page<ArchiveCardComment> comments = commentRepository.findByArchiveCard_IdAndParentIsNull(
                archiveCardId, pageRequest(page, size));
        Map<Long, Long> replyCounts = replyCounts(comments.map(ArchiveCardComment::getId).toList());
        boolean cardOwner = card.isOwnedBy(viewerMemberId);
        return ArchiveCommentPageResponse.of(comments, comment -> ArchiveCommentResponse.from(
                comment, replyCounts.getOrDefault(comment.getId(), 0L), viewerMemberId, cardOwner));
    }

    /** 원댓글의 답글 목록 (오래된 순). */
    public ArchiveCommentPageResponse getReplies(Long viewerMemberId, Long archiveCardId, Long commentId,
                                                 int page, int size) {
        ArchiveCard card = requireReadableCard(viewerMemberId, archiveCardId);
        ArchiveCardComment parent = findCommentOnCard(commentId, archiveCardId);
        if (parent.isReply()) {
            throw new CustomException(ErrorCode.ARCHIVE_004);
        }
        Page<ArchiveCardComment> replies = commentRepository.findByArchiveCard_IdAndParent_Id(
                archiveCardId, commentId, pageRequest(page, size));
        boolean cardOwner = card.isOwnedBy(viewerMemberId);
        return ArchiveCommentPageResponse.of(replies,
                reply -> ArchiveCommentResponse.from(reply, 0, viewerMemberId, cardOwner));
    }

    @Transactional
    public ArchiveCommentResponse createComment(Long memberId, Long archiveCardId, ArchiveCommentCreateRequest request) {
        ArchiveCard card = requireReadableCard(memberId, archiveCardId);
        Member author = memberRepository.findActiveById(memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.AUTH_002));

        ArchiveCardComment replyTo = request.parentCommentId() == null ? null
                : findCommentOnCard(request.parentCommentId(), archiveCardId);
        ArchiveCardComment comment = commentRepository.save(
                new ArchiveCardComment(card, author, replyTo, request.content()));

        Long parentAuthorId = replyNotificationTarget(memberId, card, replyTo);
        eventPublisher.publishEvent(new ArchiveCommentCreatedEvent(archiveCardId, comment.getId(), memberId,
                author.getNickname(), card.getOwnerMemberId(), parentAuthorId));
        return ArchiveCommentResponse.from(comment, 0, memberId, card.isOwnedBy(memberId));
    }

    @Transactional
    public ArchiveCommentResponse updateComment(Long memberId, Long commentId, ArchiveCommentUpdateRequest request) {
        ArchiveCardComment comment = findComment(commentId);
        ArchiveCard card = archiveCardAccess.requireViewable(memberId, comment.getArchiveCardId(), ErrorCode.ARCHIVE_004);
        if (!comment.isWrittenBy(memberId)) {
            throw new CustomException(ErrorCode.COMMON_004);
        }
        comment.updateContent(request.content());
        long replyCount = comment.isReply() ? 0 : replyCounts(List.of(commentId)).getOrDefault(commentId, 0L);
        return ArchiveCommentResponse.from(comment, replyCount, memberId, card.isOwnedBy(memberId));
    }

    @Transactional
    public void deleteComment(Long memberId, Long commentId) {
        ArchiveCardComment comment = findComment(commentId);
        if (!comment.isWrittenBy(memberId)) {
            ArchiveCard card = archiveCardAccess.requireViewable(memberId, comment.getArchiveCardId(), ErrorCode.ARCHIVE_004);
            if (!card.isOwnedBy(memberId)) {
                throw new CustomException(ErrorCode.COMMON_004);
            }
        }
        commentRepository.delete(comment); // 원댓글이면 답글은 DB에서 함께 지워진다 (ON DELETE CASCADE)
    }

    /**
     * 답글 알림은 실제로 답한 댓글의 작성자에게 (인스타처럼). 내 댓글에 단 답글이거나,
     * 그 사람이 이제 이 카드를 볼 수 없으면(친구 끊김·비공개 카드) 보내지 않는다.
     */
    private Long replyNotificationTarget(Long memberId, ArchiveCard card, ArchiveCardComment replyTo) {
        if (replyTo == null) {
            return null;
        }
        Long target = replyTo.getAuthor().getId();
        if (target.equals(memberId) || !archiveCardAccess.canView(target, card)) {
            return null;
        }
        return target;
    }

    private ArchiveCard requireReadableCard(Long viewerMemberId, Long archiveCardId) {
        return archiveCardAccess.requireViewable(viewerMemberId, archiveCardId, ErrorCode.ARCHIVE_002);
    }

    private ArchiveCardComment findComment(Long commentId) {
        return commentRepository.findWithAuthorById(commentId)
                .orElseThrow(() -> new CustomException(ErrorCode.ARCHIVE_004));
    }

    private ArchiveCardComment findCommentOnCard(Long commentId, Long archiveCardId) {
        ArchiveCardComment comment = findComment(commentId);
        if (!comment.getArchiveCardId().equals(archiveCardId)) {
            throw new CustomException(ErrorCode.ARCHIVE_004);
        }
        return comment;
    }

    private Map<Long, Long> replyCounts(List<Long> parentCommentIds) {
        if (parentCommentIds.isEmpty()) {
            return Map.of();
        }
        return commentRepository.countRepliesByParentCommentIds(parentCommentIds).stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));
    }

    private static PageRequest pageRequest(int page, int size) {
        int normalized = size < 1 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        return PageRequest.of(Math.max(page, 0), normalized, OLDEST_FIRST);
    }
}
