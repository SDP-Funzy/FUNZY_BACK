package com.sdp1617.backend.letter.entity;

import com.sdp1617.backend.auth.entity.Member;
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
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.DynamicUpdate;

/**
 * 편지 1통 (#82). 보내는 사람과 받는 사람이 같은 편지를 본다 — 전송은 받는 사람을 지정하고 상태를 바꾸는 것뿐,
 * 받는 쪽에 복사본을 만들지 않는다. 카드(1~5장)와 두들픽 선물 후보(2~3개)를 함께 가진다.
 */
@Getter
@Entity
// 바뀐 컬럼만 UPDATE한다. 보낸 뒤에는 받는 사람 쪽 값(읽은 시각, 받은 편지함에서 지운 시각, 고른 선물)만 바뀌는데,
// 전체 컬럼을 쓰면 동시에 처리된 다른 변경(예: 열기와 지우기)을 예전 값으로 덮어쓸 수 있다.
@DynamicUpdate
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

    /** 공유 링크 토큰 (#87). 추측할 수 없는 값. 취소하면 null, 다시 발급하면 바뀐다. */
    @Column(name = "share_token", length = 64)
    private String shareToken;

    /** 공유 링크 만료 시각 (발급 후 30일). 확정된 수신자는 만료 뒤에도 받은 편지함에서 계속 본다. */
    @Column(name = "share_token_expires_at")
    private LocalDateTime shareTokenExpiresAt;

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

    /**
     * 받는 사람에게 전달된 시각 — 받은 편지함의 받은 날짜·정렬·날짜 필터와 보관함 날짜에 쓴다.
     * 직접 보내면 보낸 시각, 공유 링크는 누군가 받은 시각(받기 전에는 링크를 만든 시각) (#87, #114).
     */
    private LocalDateTime sentAt;

    /** 받는 사람이 처음 열어본 시각 (미읽음 표시 #90). */
    private LocalDateTime readAt;

    /** 받는 사람이 받은 편지함에서 삭제한 시각. 보낸 사람의 보낸함에는 그대로 남는다. */
    private LocalDateTime recipientHiddenAt;

    // 편지함·이어쓰기 목록에서 편지마다 카드를 따로 조회하지 않도록, 한 페이지(최대 100통)의 카드를 한 번에 읽는다
    @OneToMany(mappedBy = "letter", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("cardOrder asc")
    @BatchSize(size = 100)
    private List<LetterCard> cards = new ArrayList<>();

    @OneToMany(mappedBy = "letter", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("itemOrder asc")
    @BatchSize(size = 100)
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

    /** 편지함 목록 썸네일용: 카드 순서대로 사진이 있는 카드의 사진 URL을 최대 max장 (#147). */
    public List<String> thumbnailImageUrls(int max) {
        return cards.stream()
                .map(LetterCard::getImageUrl)
                .filter(Objects::nonNull)
                .limit(max)
                .toList();
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

    /**
     * 회원에게 보낸다 (#86). 완료한 편지만 보낼 수 있고, 보낸 뒤에는 수정할 수 없다.
     * 공유 링크로 보냈지만 아직 아무도 받지 않은 편지도 회원에게 보낼 수 있고, 그 회원이 받는 사람이 된다 (#114).
     * 받는 사람이 유효한지(탈퇴·본인 여부)는 서비스에서 확인한다.
     */
    public void sendTo(Member recipient) {
        if (isWaitingForRecipient()) {
            deliverTo(recipient);
            return;
        }
        if (status == LetterStatus.SENT) {
            throw new CustomException(ErrorCode.LETTER_009);
        }
        if (status != LetterStatus.COMPLETED) {
            throw new CustomException(ErrorCode.LETTER_006);
        }
        this.recipient = recipient;
        markSent();
    }

    /**
     * 공유 링크를 발급한다 (#87). 완료한 편지는 이때 보낸 편지가 되어 더 이상 수정할 수 없고, 받는 사람은 링크로 정해진다.
     * 회원에게 직접 보낸 편지도 링크를 만들 수 있다(받는 사람은 그대로). 다시 발급하면 이전 링크는 무효.
     */
    public void issueShareLink(String token, LocalDateTime expiresAt) {
        if (status == LetterStatus.DRAFT) {
            throw new CustomException(ErrorCode.LETTER_006);
        }
        if (status == LetterStatus.COMPLETED) {
            markSent();
        }
        this.shareToken = token;
        this.shareTokenExpiresAt = expiresAt;
    }

    /** 공유 링크를 취소한다. 이미 정해진 받는 사람은 그대로 받은 편지함에서 본다. */
    public void revokeShareLink() {
        this.shareToken = null;
        this.shareTokenExpiresAt = null;
    }

    public boolean isShareLinkValid(LocalDateTime now) {
        return shareToken != null && shareTokenExpiresAt != null && shareTokenExpiresAt.isAfter(now);
    }

    /** 링크로 보냈고 아직 아무도 받지 않은 편지. */
    public boolean isWaitingForRecipient() {
        return status == LetterStatus.SENT && recipient == null;
    }

    /** 링크로 받은 회원을 받는 사람으로 정한다 (#114). 받을 수 있는지는 서비스에서 편지 행을 잠그고 확인한다. */
    public void receiveViaLink(Member recipient) {
        if (!isWaitingForRecipient()) {
            throw new CustomException(ErrorCode.LETTER_012);
        }
        deliverTo(recipient);
    }

    /** 링크로 보낸 편지의 받는 사람이 정해진 때가 받는 사람에게 전달된 시각이다. */
    private void deliverTo(Member recipient) {
        this.recipient = recipient;
        this.sentAt = LocalDateTime.now();
        touch();
    }

    private void markSent() {
        this.status = LetterStatus.SENT;
        this.sentAt = LocalDateTime.now();
        touch();
    }

    /** 받는 사람이 볼 수 있는 편지인지: 받은 편지이고, 받은 편지함에서 지우지 않았어야 한다. */
    public boolean isVisibleToRecipient(Long memberId) {
        return status == LetterStatus.SENT
                && recipient != null
                && recipient.getId().equals(memberId)
                && recipientHiddenAt == null;
    }

    /** 받는 사람이 처음 열어본 시각을 남긴다 (미읽음 표시 #90). */
    public void markReadByRecipient() {
        if (readAt == null) {
            readAt = LocalDateTime.now();
        }
    }

    /** 받는 사람이 두들픽 선물 후보 중 하나를 고른다 (LR-022). 다시 고르면 바뀌고, null이면 선택을 취소한다. */
    public void selectGift(Long giftItemId) {
        if (giftItemId != null && giftItems.stream().noneMatch(item -> Objects.equals(item.getId(), giftItemId))) {
            throw new CustomException(ErrorCode.LETTER_010);
        }
        this.selectedGiftItemId = giftItemId;
    }

    /** 받는 사람이 받은 편지함에서 지운다. 보낸 사람의 보낸 편지함에는 남는다. */
    public void hideForRecipient() {
        recipientHiddenAt = LocalDateTime.now();
    }

    /** 받은 편지함에서 지운 받는 사람이 공유 링크로 다시 받으면 받은 편지함에 되돌린다 (#114). 지울 때 정리한 반응은 돌아오지 않는다. */
    public void restoreForRecipient() {
        recipientHiddenAt = null;
    }

    public boolean isRecipient(Long memberId) {
        return recipient != null && recipient.getId().equals(memberId);
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
