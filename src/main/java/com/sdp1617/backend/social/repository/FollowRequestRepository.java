package com.sdp1617.backend.social.repository;

import com.sdp1617.backend.social.entity.FollowRequest;
import java.util.Collection;
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

    /** 나와 다른 회원들 사이의 요청 (내가 보냈거나 받은 것). 검색 결과의 관계 상태 표시용 (#153). */
    @Query("""
            select r from FollowRequest r
            where (r.requesterId = :memberId and r.receiverId in :otherIds)
               or (r.receiverId = :memberId and r.requesterId in :otherIds)
            """)
    List<FollowRequest> findBetweenMemberAnd(@Param("memberId") Long memberId,
                                             @Param("otherIds") Collection<Long> otherIds);

    @Modifying
    @Query("delete from FollowRequest r where r.requesterId = :memberId or r.receiverId = :memberId")
    int deleteAllByMember(@Param("memberId") Long memberId);
}
