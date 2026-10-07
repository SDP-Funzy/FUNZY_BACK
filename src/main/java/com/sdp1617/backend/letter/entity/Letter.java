package com.sdp1617.backend.letter.entity;

import com.sdp1617.backend.auth.entity.Member;
import com.sdp1617.backend.card.dto.DesignType;
import com.sdp1617.backend.global.error.CustomException;
import com.sdp1617.backend.global.error.ErrorCode;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 편지 1통 (#82). 보내는 사람과 받는 사람이 같은 편지를 본다 — 전송은 받는 사람을 지정하고 상태를 바꾸는 것뿐,
 * 받는 쪽에 복사본을 만들지 않는다. 카드(1~5장)와 두들픽 선물 후보(2~3개)를 함께 가진다.
 */
@Getter
@Entity
@Table(
        name = "letters",
        uniqueConstraints = @UniqueConstraint(name = "uk_letter_share_token", columnNames = "share_token")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Letter {

    public static final int MAX_CARD_COUNT = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    private Member sender;

    /** 받는 사람. 링크로 보내는 경우(#87) 받는 사람이 확정되기 전까지 null. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_id")
    private Member recipient;

    /** 봉투에 적는 받는 사람 이름 (LW-111). */
    @Column(name = "to_name", nullable = false, length = 10)
    private String toName;

    /** 봉투에 적는 보내는 사람 이름 (LW-112). */
    @Column(name = "from_name", nullable = false, length = 10)
    private String fromName;

    @Enumerated(EnumType.STRING)
    @Column(name = "design_type", nullable = false, length = 20)
    private DesignType designType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LetterStatus status;

    /** 링크 전송용 토큰 (#87). */
    @Column(name = "share_token", length = 64)
    private String shareToken;

    /** 두들픽 선정 이유. 두들픽이 없으면 null. */
    @Column(name = "gift_reason", length = 150)
    private String giftReason;

    /** 받는 사람이 고른 선물 (LR-022). */
    @Column(name = "selected_gift_item_id")
    private Long selectedGiftItemId;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    private LocalDateTime completedAt;

    private LocalDateTime sentAt;

    /** 받는 사람이 처음 열어본 시각 (미읽음 표시 #90). */
    private LocalDateTime readAt;

    /** 받는 사람이 받은 편지함에서 삭제한 시각. 보낸 사람의 보낸함에는 그대로 남는다. */
    private LocalDateTime recipientHiddenAt;

    @OneToMany(mappedBy = "letter", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("cardOrder asc")
    private List<LetterCard> cards = new ArrayList<>();

    @OneToMany(mappedBy = "letter", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("itemOrder asc")
    private List<GiftItem> giftItems = new ArrayList<>();

    private Letter(Member sender, String toName, String fromName, DesignType designType) {
        this.sender = sender;
        this.toName = toName;
        this.fromName = fromName;
        this.designType = designType;
        this.status = LetterStatus.DRAFT;
    }

    public static Letter start(Member sender, String toName, String fromName, DesignType designType) {
        return new Letter(sender, toName, fromName, designType);
    }

    public boolean isWrittenBy(Long memberId) {
        return sender.getId().equals(memberId);
    }

    public void updateEnvelope(String toName, String fromName, DesignType designType) {
        requireEditable();
        this.toName = toName;
        this.fromName = fromName;
        this.designType = designType;
        touch();
    }

    public LetterCard addCard(LetterCardContent content) {
        requireEditable();
        if (cards.size() >= MAX_CARD_COUNT) {
            throw new CustomException(ErrorCode.LETTER_002);
        }
        LetterCard card = new LetterCard(this, cards.size() + 1, content);
        cards.add(card);
        touch();
        return card;
    }

    public LetterCard updateCard(Long cardId, LetterCardContent content) {
        requireEditable();
        LetterCard card = findCard(cardId);
        card.update(content);
        touch();
        return card;
    }

    /**
     * 카드를 지우고 남은 카드 순서를 1부터 다시 매긴다. 완료된 편지의 마지막 카드를 지우면 완료 조건(카드 1장 이상)을
     * 잃으므로 작성 중으로 되돌린다.
     */
    public LetterCard removeCard(Long cardId) {
        requireEditable();
        LetterCard card = findCard(cardId);
        cards.remove(card);
        for (int i = 0; i < cards.size(); i++) {
            cards.get(i).changeOrder(i + 1);
        }
        if (cards.isEmpty() && status == LetterStatus.COMPLETED) {
            status = LetterStatus.DRAFT;
            completedAt = null;
        }
        touch();
        return card;
    }

    /** 두들픽 전체 교체. 선물 후보는 2~3개 (개수는 요청 검증에서 확인). */
    public void replaceDoodlePick(String reason, List<String> giftNames) {
        requireEditable();
        giftItems.clear();
        for (int i = 0; i < giftNames.size(); i++) {
            giftItems.add(new GiftItem(this, i + 1, giftNames.get(i)));
        }
        this.giftReason = reason;
        touch();
    }

    public void removeDoodlePick() {
        requireEditable();
        giftItems.clear();
        this.giftReason = null;
        touch();
    }

    public boolean hasDoodlePick() {
        return !giftItems.isEmpty();
    }

    /** 편지 완료 (LW-710). 카드가 1장 이상이어야 한다. 이미 완료된 편지는 그대로 둔다. */
    public void complete() {
        requireEditable();
        if (cards.isEmpty()) {
            throw new CustomException(ErrorCode.LETTER_004);
        }
        if (status == LetterStatus.DRAFT) {
            status = LetterStatus.COMPLETED;
            completedAt = LocalDateTime.now();
        }
        touch();
    }

    /** 전송 전(작성 중·완료)에는 수정할 수 있고, 전송된 편지는 수정할 수 없다 (LW-020). */
    private void requireEditable() {
        if (status == LetterStatus.SENT) {
            throw new CustomException(ErrorCode.LETTER_003);
        }
    }

    private LetterCard findCard(Long cardId) {
        return cards.stream()
                .filter(card -> Objects.equals(card.getId(), cardId))
                .findFirst()
                .orElseThrow(() -> new CustomException(ErrorCode.LETTER_005));
    }

    /** 카드·두들픽만 바뀌어도 편지의 수정 시각이 갱신되게 한다 (이어쓰기 목록 정렬용). */
    private void touch() {
        this.updatedAt = LocalDateTime.now();
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
