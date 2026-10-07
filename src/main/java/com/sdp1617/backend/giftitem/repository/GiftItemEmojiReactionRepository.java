package com.sdp1617.backend.giftitem.repository;

import com.sdp1617.backend.giftitem.entity.GiftItemEmojiReaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;

public interface GiftItemEmojiReactionRepository extends JpaRepository<GiftItemEmojiReaction, Long> {

    Optional<GiftItemEmojiReaction> findByGiftItemIdAndMemberId(Long giftItemId, Long memberId);

    void deleteByMemberIdAndGiftItemIdIn(Long memberId, Collection<Long> giftItemIds);
}
