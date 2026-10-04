package com.sdp1617.backend.social.repository;

import com.sdp1617.backend.social.entity.FollowRequest;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FollowRequestRepository extends JpaRepository<FollowRequest, Long> {

    boolean existsByRequesterIdAndReceiverId(Long requesterId, Long receiverId);

    Optional<FollowRequest> findByRequesterIdAndReceiverId(Long requesterId, Long receiverId);

    List<FollowRequest> findByReceiverIdOrderByCreatedAtDesc(Long receiverId);

    List<FollowRequest> findByRequesterIdOrderByCreatedAtDesc(Long requesterId);

    @Modifying
    @Query("delete from FollowRequest r where r.requesterId = :memberId or r.receiverId = :memberId")
    int deleteAllByMember(@Param("memberId") Long memberId);
}
