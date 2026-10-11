package com.sdp1617.backend.notification.repository;

import com.sdp1617.backend.notification.entity.PushSubscription;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, Long> {

    Optional<PushSubscription> findByEndpoint(String endpoint);

    List<PushSubscription> findByMemberIdOrderByUpdatedAtDesc(Long memberId);

    @Transactional
    @Modifying
    @Query("delete from PushSubscription s where s.memberId = :memberId and s.endpoint = :endpoint")
    int deleteByMemberIdAndEndpoint(@Param("memberId") Long memberId, @Param("endpoint") String endpoint);

    @Transactional
    @Modifying
    @Query("delete from PushSubscription s where s.memberId = :memberId")
    int deleteByMemberId(@Param("memberId") Long memberId);

    /**
     * 만료된 구독 정리. 발송하는 동안 같은 브라우저가 다시 등록했으면(키 갱신·다른 회원으로 옮김) 등록 시각이 바뀌므로
     * 지우지 않는다 — 발송할 때 읽은 그 구독일 때만 지운다.
     * 발송은 트랜잭션 밖(비동기)에서 하므로 여기서 트랜잭션을 연다.
     */
    @Transactional
    @Modifying
    @Query("delete from PushSubscription s where s.id = :id and s.updatedAt = :updatedAt")
    int deleteIfUnchanged(@Param("id") Long id, @Param("updatedAt") LocalDateTime updatedAt);
}
