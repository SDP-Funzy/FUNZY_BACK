package com.sdp1617.backend.auth.entity;

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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 회원 1명이 여러 소셜 로그인 수단을 동시에 연결할 수 있도록, Member.provider/providerId
 * (회원당 하나만 표현 가능)와 별개로 소셜 연결만 다루는 테이블. LOCAL(아이디/비밀번호)은
 * Member.password 존재 여부로 이미 판별 가능하므로 여기엔 소셜 연결만 저장한다.
 * uk_social_connection_member_provider: 회원 한 명이 같은 provider를 두 개(예: 카카오 계정 2개)
 * 연결하는 걸 막는다 — 연결 추가 API의 "본인 계정에 이미 연결된 provider면 거부" 규칙을
 * 애플리케이션 체크뿐 아니라 DB 제약으로도 강제해, 동시 요청 경쟁에서도 깨지지 않게 한다.
 */
@Entity
@Table(
        name = "social_connections",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_social_connection_provider_provider_id",
                        columnNames = {"provider", "provider_id"}
                ),
                @UniqueConstraint(
                        name = "uk_social_connection_member_provider",
                        columnNames = {"member_id", "provider"}
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SocialConnection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuthProvider provider;

    @Column(name = "provider_id", nullable = false, length = 255)
    private String providerId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private SocialConnection(Member member, AuthProvider provider, String providerId) {
        if (provider == AuthProvider.LOCAL) {
            throw new IllegalArgumentException("LOCAL은 SocialConnection 대상이 아닙니다.");
        }
        this.member = member;
        this.provider = provider;
        this.providerId = providerId;
        this.createdAt = LocalDateTime.now();
    }

    public static SocialConnection create(Member member, AuthProvider provider, String providerId) {
        return new SocialConnection(member, provider, providerId);
    }
}
