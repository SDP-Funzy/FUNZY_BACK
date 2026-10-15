package com.sdp1617.backend.archive.entity;

import com.sdp1617.backend.auth.entity.Member;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;

/**
 * 아카이브 카드 댓글 (#164, 인스타식). 답글은 한 단계만: parentCommentId는 항상 원댓글을 가리킨다.
 * 작성자가 탈퇴해도 댓글은 남고 작성자는 "탈퇴한회원N"으로 보인다 (회원 행을 익명화, #93).
 * 카드가 지워지면 댓글이, 원댓글이 지워지면 답글이 DB에서 함께 지워진다 (ON DELETE CASCADE) —
 * 지우는 경로(콕 취소·받은 편지 삭제·탈퇴 등)마다 따로 챙기지 않아도 되고, 동시에 달린 댓글도 남지 않는다.
 */
@Getter
@Entity
@Table(
        name = "archive_card_comments",
        indexes = @Index(name = "idx_archive_card_comment_card_parent", columnList = "archive_card_id, parent_comment_id")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ArchiveCardComment {

    public static final int MAX_CONTENT_LENGTH = 200;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "archive_card_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private ArchiveCard archiveCard;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member author;

    /** 원댓글이면 null, 답글이면 원댓글. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_comment_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private ArchiveCardComment parent;

    @Column(nullable = false, length = MAX_CONTENT_LENGTH)
    private String content;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 수정한 적이 없으면 null. */
    private LocalDateTime updatedAt;

    /** replyTo: 답할 댓글 (원댓글이면 null). 답글에 답해도 같은 원댓글 아래로 모은다(한 단계). */
    public ArchiveCardComment(ArchiveCard archiveCard, Member author, ArchiveCardComment replyTo, String content) {
        this.archiveCard = archiveCard;
        this.author = author;
        this.parent = replyTo == null ? null : replyTo.root();
        this.content = content;
        this.createdAt = LocalDateTime.now();
    }

    public Long getArchiveCardId() {
        return archiveCard.getId();
    }

    public Long getParentCommentId() {
        return parent == null ? null : parent.getId();
    }

    public boolean isReply() {
        return parent != null;
    }

    private ArchiveCardComment root() {
        return isReply() ? parent : this;
    }

    public boolean isWrittenBy(Long memberId) {
        return author.getId().equals(memberId);
    }

    public void updateContent(String content) {
        this.content = content;
        this.updatedAt = LocalDateTime.now();
    }
}
