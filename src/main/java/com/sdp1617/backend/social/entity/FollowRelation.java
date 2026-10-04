package com.sdp1617.backend.social.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(
        name = "follow_relations",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_follow_relation_pair",
                columnNames = {"member_id_a", "member_id_b"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FollowRelation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id_a", nullable = false)
    private Long memberIdA;

    @Column(name = "member_id_b", nullable = false)
    private Long memberIdB;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private FollowRelation(Long memberIdA, Long memberIdB) {
        this.memberIdA = memberIdA;
        this.memberIdB = memberIdB;
        this.createdAt = LocalDateTime.now();
    }

    /** 이 관계에서 memberId가 아닌 쪽(친구)의 회원 ID. */
    public Long otherMemberId(Long memberId) {
        return memberIdA.equals(memberId) ? memberIdB : memberIdA;
    }

    public static FollowRelation of(Long memberId1, Long memberId2) {
        long a = Math.min(memberId1, memberId2);
        long b = Math.max(memberId1, memberId2);
        return new FollowRelation(a, b);
    }
}
