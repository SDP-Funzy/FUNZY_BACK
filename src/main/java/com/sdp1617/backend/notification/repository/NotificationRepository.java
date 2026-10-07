package com.sdp1617.backend.notification.repository;

import com.sdp1617.backend.notification.entity.Notification;
import com.sdp1617.backend.notification.entity.NotificationType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByMemberIdOrderByCreatedAtDesc(Long memberId);

    long countByMemberIdAndReadFalse(Long memberId);

    /** 이모지 알림 중복 확인. cardId가 null이면 card_id is null로 찾는다 (두들픽 선물 이모지). */
    boolean existsByMemberIdAndActorMemberIdAndTypeAndLetterIdAndCardId(
            Long memberId, Long actorMemberId, NotificationType type, Long letterId, Long cardId);

    void deleteByMemberId(Long memberId);

    void deleteByMemberIdAndLetterId(Long memberId, Long letterId);
}
