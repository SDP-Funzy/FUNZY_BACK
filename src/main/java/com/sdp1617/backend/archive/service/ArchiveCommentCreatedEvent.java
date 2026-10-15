package com.sdp1617.backend.archive.service;

/**
 * 아카이브 카드에 댓글·답글이 달렸다 (#164). 카드 주인과 답한 댓글의 작성자에게 알림을 만든다.
 * parentAuthorId는 답글일 때 실제로 답한 댓글의 작성자(알림 받을 사람), 원댓글이거나 알림 대상이 아니면 null.
 */
public record ArchiveCommentCreatedEvent(
        Long archiveCardId,
        Long commentId,
        Long authorId,
        String authorNickname,
        Long cardOwnerId,
        Long parentAuthorId
) {
}
