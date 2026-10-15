package com.sdp1617.backend.archive.dto;

import com.sdp1617.backend.archive.entity.ArchiveCardComment;
import com.sdp1617.backend.auth.entity.Member;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

public record ArchiveCommentResponse(
        @Schema(description = "댓글 ID", example = "3")
        Long commentId,
        @Schema(description = "원댓글이면 null, 답글이면 원댓글 ID", example = "null")
        Long parentCommentId,
        @Schema(description = "작성자. 탈퇴한 회원은 닉네임이 \"탈퇴한회원N\"")
        Author author,
        @Schema(description = "내용", example = "이 노래 나도 좋아해!")
        String content,
        @Schema(description = "답글 수 (답글이면 0)", example = "2")
        long replyCount,
        @Schema(description = "내가 쓴 댓글인지 (수정 버튼 표시)", example = "false")
        boolean mine,
        @Schema(description = "내가 지울 수 있는지 (내 댓글이거나 내 카드의 댓글)", example = "true")
        boolean deletable,
        @Schema(description = "수정한 적이 있는지", example = "false")
        boolean edited,
        LocalDateTime createdAt
) {
    public record Author(
            @Schema(description = "회원 ID", example = "2")
            Long memberId,
            @Schema(description = "닉네임(아이디)", example = "tiki")
            String nickname,
            @Schema(description = "프로필 이미지 URL. 기본 이미지면 null")
            String profileImageUrl
    ) {
        static Author from(Member member) {
            return new Author(member.getId(), member.getNickname(), member.getProfileImageUrl());
        }
    }

    /** cardOwner: 보는 사람이 카드 주인인지 (남의 댓글도 지울 수 있다). */
    public static ArchiveCommentResponse from(ArchiveCardComment comment, long replyCount, Long viewerMemberId,
                                              boolean cardOwner) {
        boolean mine = comment.isWrittenBy(viewerMemberId);
        return new ArchiveCommentResponse(comment.getId(), comment.getParentCommentId(),
                Author.from(comment.getAuthor()), comment.getContent(), replyCount, mine, mine || cardOwner,
                comment.getUpdatedAt() != null, comment.getCreatedAt());
    }
}
