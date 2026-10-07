package com.sdp1617.backend.archive.entity;

import com.sdp1617.backend.letter.entity.Letter;
import com.sdp1617.backend.letter.entity.LetterCard;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Entity
@Table(
        name = "archive_cards",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_archive_card_owner_letter",
                columnNames = {"owner_member_id", "letter_card_id"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ArchiveCard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_member_id", nullable = false)
    private Long ownerMemberId;

    @Column(name = "letter_card_id", nullable = false)
    private Long letterCardId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ArchiveCategory category;

    @Column(length = 100)
    private String senderName;

    @Column(length = 100)
    private String receiverName;

    private LocalDate letterDate;

    @Column(length = 500)
    private String imageUrl;

    @Column(length = 2000)
    private String message;

    @Column(nullable = false)
    private int likeCount;

    @Embedded
    private ArchiveVisibility visibility = new ArchiveVisibility();

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public ArchiveCard(Long ownerMemberId, Long letterCardId, ArchiveCategory category) {
        this.ownerMemberId = ownerMemberId;
        this.letterCardId = letterCardId;
        this.category = category;
        this.createdAt = LocalDateTime.now();
    }

    /**
     * 받은 편지 카드를 아카이브에 담는다 (콕, LR-310). 보여줄 내용(보낸·받는 사람 이름, 날짜, 사진, 메시지)을 저장 시점에 복사해 둔다.
     * 아카이브는 내 보드라, 원본 편지를 받은 편지함에서 지워도 담아 둔 카드 내용은 따로 관리한다(편지 삭제 시 함께 정리 #82).
     */
    public static ArchiveCard ofLetterCard(Long ownerMemberId, LetterCard card, ArchiveCategory category) {
        ArchiveCard archiveCard = new ArchiveCard(ownerMemberId, card.getId(), category);
        Letter letter = card.getLetter();
        archiveCard.senderName = letter.getFromName();
        archiveCard.receiverName = letter.getToName();
        archiveCard.letterDate = letter.getSentAt() == null ? null : letter.getSentAt().toLocalDate();
        archiveCard.imageUrl = card.getImageUrl();
        archiveCard.message = card.getContent();
        return archiveCard;
    }

    public boolean isOwnedBy(Long memberId) {
        return ownerMemberId.equals(memberId);
    }

    public void updateVisibility(ArchiveVisibility visibility) {
        this.visibility = visibility;
    }

    public void increaseLikeCount() {
        this.likeCount++;
    }

    public void decreaseLikeCount() {
        if (this.likeCount > 0) {
            this.likeCount--;
        }
    }
}
