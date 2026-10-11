package com.sdp1617.backend.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 브라우저 하나의 웹 푸시 구독 (#89). 구독 주소(endpoint)는 브라우저마다 달라서, 같은 브라우저에서 다른 회원이
 * 로그인해 등록하면 그 회원의 구독으로 옮긴다 — 한 브라우저에 앞 회원의 알림이 가지 않게.
 */
@Getter
@Entity
@Table(name = "push_subscriptions", indexes = @Index(name = "idx_push_subscription_member", columnList = "member_id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PushSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(nullable = false, unique = true, length = 1024)
    private String endpoint;

    /** 브라우저의 P-256 공개키 (base64url). 본문 암호화에 쓴다. */
    @Column(nullable = false, length = 100)
    private String p256dh;

    /** 브라우저가 만든 16바이트 비밀값 (base64url). 본문 암호화에 쓴다. */
    @Column(nullable = false, length = 50)
    private String auth;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 마지막으로 등록(갱신)한 시각. 회원당 구독 수를 넘으면 오래된 것부터 지운다. */
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public PushSubscription(Long memberId, String endpoint, String p256dh, String auth) {
        this.memberId = memberId;
        this.endpoint = endpoint;
        this.p256dh = p256dh;
        this.auth = auth;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    public void renew(Long memberId, String p256dh, String auth) {
        this.memberId = memberId;
        this.p256dh = p256dh;
        this.auth = auth;
        this.updatedAt = LocalDateTime.now();
    }
}
