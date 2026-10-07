package com.sdp1617.backend.heartcard.repository;

import com.sdp1617.backend.heartcard.entity.HeartCardEmojiReaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.Collection;

public interface HeartCardEmojiReactionRepository extends JpaRepository<HeartCardEmojiReaction, Long> {

    Optional<HeartCardEmojiReaction> findByHeartCardIdAndMemberId(Long heartCardId, Long memberId);


    void deleteByMemberIdAndHeartCardIdIn(Long memberId, Collection<Long> heartCardIds);
}
