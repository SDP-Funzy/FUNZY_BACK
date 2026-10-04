package com.sdp1617.backend.social.repository;

import com.sdp1617.backend.social.entity.FollowRelation;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FollowRelationRepository extends JpaRepository<FollowRelation, Long> {

    @Query("select f from FollowRelation f where f.memberIdA = :memberId or f.memberIdB = :memberId")
    List<FollowRelation> findAllByMember(@Param("memberId") Long memberId);

    @Query("select f from FollowRelation f where f.memberIdA = :memberId or f.memberIdB = :memberId "
            + "order by f.createdAt desc, f.id desc")
    List<FollowRelation> findAllByMemberOrderByNewest(@Param("memberId") Long memberId);

    @Modifying
    @Query("delete from FollowRelation f where f.memberIdA = :memberId or f.memberIdB = :memberId")
    int deleteAllByMember(@Param("memberId") Long memberId);

    @Query("select count(f) from FollowRelation f where f.memberIdA = :memberId or f.memberIdB = :memberId")
    long countByMember(@Param("memberId") Long memberId);

    @Query("select f from FollowRelation f where "
            + "(f.memberIdA = :memberId1 and f.memberIdB = :memberId2) "
            + "or (f.memberIdA = :memberId2 and f.memberIdB = :memberId1)")
    Optional<FollowRelation> findBetween(@Param("memberId1") Long memberId1, @Param("memberId2") Long memberId2);
}
