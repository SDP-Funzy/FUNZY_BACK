package com.sdp1617.backend.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "notifications")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationType type;

    @Column(nullable = false, length = 500)
    private String content;

    @Column(nullable = false)
    private boolean read;

    /** 알림을 일으킨 회원 (보낸 사람·반응한 사람·친구 요청한 사람). */
    @Column(name = "actor_member_id")
    private Long actorMemberId;

    /** 알림을 눌렀을 때 열 편지. 편지 관련 알림에만 있다. */
    @Column(name = "letter_id")
    private Long letterId;

    /** 알림을 눌렀을 때 보여줄 마음카드. 마음카드 반응 알림에만 있다. */
    @Column(name = "card_id")
    private Long cardId;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public Notification(Long memberId, NotificationType type, String content) {
        this(memberId, type, content, null, null, null);
    }

    public Notification(Long memberId, NotificationType type, String content,
                        Long actorMemberId, Long letterId, Long cardId) {
        this.memberId = memberId;
        this.type = type;
        this.content = content;
        this.read = false;
        this.actorMemberId = actorMemberId;
        this.letterId = letterId;
        this.cardId = cardId;
        this.createdAt = LocalDateTime.now();
    }

    public boolean isOwnedBy(Long memberId) {
        return this.memberId.equals(memberId);
    }

    public void markAsRead() {
        this.read = true;
    }
}
