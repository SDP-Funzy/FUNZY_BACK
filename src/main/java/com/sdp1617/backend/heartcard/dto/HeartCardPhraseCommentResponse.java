package com.sdp1617.backend.heartcard.dto;

import com.sdp1617.backend.heartcard.entity.HeartCardPhraseComment;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

public record HeartCardPhraseCommentResponse(
        Long commentId,
        Long heartCardId,
        Long memberId,
        int startOffset,
        int endOffset,
        String selectedText,
        String content,
        boolean mine,
        @Schema(description = "이번 요청으로 보낸 사람에게 알림을 만들었는지. 코멘트 작성 시 true (보낸 사람이 탈퇴했으면 false). 수정·조회는 false")
        boolean notificationCreated,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static HeartCardPhraseCommentResponse from(HeartCardPhraseComment comment, Long viewerMemberId) {
        return of(comment, viewerMemberId, false);
    }

    public static HeartCardPhraseCommentResponse of(
            HeartCardPhraseComment comment,
            Long viewerMemberId,
            boolean notificationCreated
    ) {
        return new HeartCardPhraseCommentResponse(
                comment.getId(),
                comment.getHeartCardId(),
                comment.getMemberId(),
                comment.getStartOffset(),
                comment.getEndOffset(),
                comment.getSelectedText(),
                comment.getContent(),
                comment.isWrittenBy(viewerMemberId),
                notificationCreated,
                comment.getCreatedAt(),
                comment.getUpdatedAt()
        );
    }
}
