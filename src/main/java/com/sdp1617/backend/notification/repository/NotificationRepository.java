package com.sdp1617.backend.notification.repository;

import com.sdp1617.backend.notification.entity.Notification;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByMemberIdOrderByCreatedAtDesc(Long memberId);

    void deleteByMemberId(Long memberId);
}
