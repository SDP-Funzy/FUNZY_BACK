package com.sdp1617.backend.archive.entity;

import com.sdp1617.backend.letter.entity.Letter;
import com.sdp1617.backend.letter.entity.LetterCard;

import jakarta.persistence.Column;
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
import org.hibernate.annotations.ColumnDefault;

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

    /**
     * 비공개(잠금) 여부 (#147). 공개면 아카이브에 보이고 친구도 본다.
     * 비공개면 내 아카이브·친구 아카이브 어디에도 안 보이고, 편지 보관함의 잠긴편지 필터로만 찾는다.
     * 기존 행이 있는 표에 칸을 추가하므로 DB 기본값을 둔다 (기존 카드는 공개).
     */
    @ColumnDefault("false")
    @Column(nullable = false)
    private boolean locked;

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
    public static ArchiveCard ofLetterCard(Long ownerMemberId, LetterCard card, ArchiveCategory category, boolean locked) {
        ArchiveCard archiveCard = new ArchiveCard(ownerMemberId, card.getId(), category);
        archiveCard.locked = locked;
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

    public void updateLocked(boolean locked) {
        this.locked = locked;
    }
}
