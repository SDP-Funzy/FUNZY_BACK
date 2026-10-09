package com.sdp1617.backend.letter.entity;

import com.sdp1617.backend.archive.entity.ArchiveCategory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 편지 속 카드(마음카드) 1장. 받는 쪽의 이모지·문구 코멘트·콕(아카이브)은 이 카드의 ID를 가리킨다
 * (heart_card_emoji_reactions.heart_card_id 등 — #82).
 */
@Getter
@Entity
@Table(name = "letter_cards")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LetterCard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "letter_id", nullable = false)
    private Letter letter;

    @Column(name = "card_order", nullable = false)
    private int cardOrder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ArchiveCategory category;

    /** 카테고리 "기타"를 고르고 직접 입력한 이름 (LW-211). 기타가 아니면 null. */
    @Column(name = "custom_category", length = 10)
    private String customCategory;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(length = 1000)
    private String link;

    @Column(name = "link_title", length = 100)
    private String linkTitle;

    @Column(name = "image_key", length = 500)
    private String imageKey;

    @Column(name = "image_url", length = 1000)
    private String imageUrl;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    LetterCard(Letter letter, int cardOrder, LetterCardContent content) {
        this.letter = letter;
        this.cardOrder = cardOrder;
        apply(content);
    }

    void update(LetterCardContent content) {
        apply(content);
    }

    void changeOrder(int cardOrder) {
        this.cardOrder = cardOrder;
    }

    private void apply(LetterCardContent content) {
        this.category = content.category();
        this.customCategory = content.category() == ArchiveCategory.ETC ? content.customCategory() : null;
        this.content = content.content();
        this.link = content.link();
        this.linkTitle = content.linkTitle();
        this.imageKey = content.imageKey();
        this.imageUrl = content.imageUrl();
    }

    @PrePersist
    private void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    private void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
