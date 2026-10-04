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
        name = "follow_requests",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_follow_request_requester_receiver",
                columnNames = {"requester_id", "receiver_id"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FollowRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "requester_id", nullable = false)
    private Long requesterId;

    @Column(name = "receiver_id", nullable = false)
    private Long receiverId;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public FollowRequest(Long requesterId, Long receiverId) {
        this.requesterId = requesterId;
        this.receiverId = receiverId;
        this.createdAt = LocalDateTime.now();
    }

    public boolean isReceivedBy(Long memberId) {
        return this.receiverId.equals(memberId);
    }

    public boolean isSentBy(Long memberId) {
        return this.requesterId.equals(memberId);
    }
}
