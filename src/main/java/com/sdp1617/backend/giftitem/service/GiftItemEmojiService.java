package com.sdp1617.backend.giftitem.service;

import com.sdp1617.backend.giftitem.dto.GiftItemEmojiRequest;
import com.sdp1617.backend.giftitem.dto.GiftItemEmojiResponse;
import com.sdp1617.backend.giftitem.entity.GiftItemEmojiReaction;
import com.sdp1617.backend.giftitem.repository.GiftItemEmojiReactionRepository;
import com.sdp1617.backend.global.error.CustomException;
import com.sdp1617.backend.global.error.ErrorCode;
import com.sdp1617.backend.letter.entity.GiftItem;
import com.sdp1617.backend.letter.service.LetterReactedEvent;
import com.sdp1617.backend.letter.service.ReceivedLetterAccess;
import com.sdp1617.backend.heartcard.entity.HeartCardEmojiAction;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GiftItemEmojiService {

    private final GiftItemEmojiReactionRepository giftItemEmojiReactionRepository;
    private final ReceivedLetterAccess receivedLetterAccess;
    private final ApplicationEventPublisher eventPublisher;

    public GiftItemEmojiResponse getMyEmoji(Long memberId, Long giftItemId) {
        requireLogin(memberId);
        receivedLetterAccess.requireReceivedGiftItem(memberId, giftItemId);
        return giftItemEmojiReactionRepository.findByGiftItemIdAndMemberId(giftItemId, memberId)
                .map(GiftItemEmojiResponse::from)
                .orElseGet(() -> GiftItemEmojiResponse.empty(giftItemId));
    }

    @Transactional
    public GiftItemEmojiResponse updateEmoji(Long memberId, Long giftItemId, GiftItemEmojiRequest request) {
        requireLogin(memberId);
        GiftItem giftItem = receivedLetterAccess.lockReceivedGiftItem(memberId, giftItemId);
        return giftItemEmojiReactionRepository.findByGiftItemIdAndMemberId(giftItemId, memberId)
                .map(reaction -> updateOrDelete(giftItem.getId(), reaction, request))
                .orElseGet(() -> create(memberId, giftItem, request));
    }

    /** 남길 때 보낸 사람에게 알림 (LR-614). 바꾸거나 지울 때는 알림 없고, 지웠다 다시 남겨도 편지마다 한 번만 (알림 쪽에서 거름). */
    private GiftItemEmojiResponse create(Long memberId, GiftItem giftItem, GiftItemEmojiRequest request) {
        GiftItemEmojiReaction reaction = giftItemEmojiReactionRepository.save(
                new GiftItemEmojiReaction(giftItem.getId(), memberId, request.emoji())
        );
        eventPublisher.publishEvent(
                LetterReactedEvent.of(LetterReactedEvent.Kind.GIFT_EMOJI, giftItem.getLetter(), null));
        return GiftItemEmojiResponse.of(reaction.getGiftItemId(), reaction.getEmoji(), HeartCardEmojiAction.CREATED, false);
    }

    private GiftItemEmojiResponse updateOrDelete(
            Long giftItemId,
            GiftItemEmojiReaction reaction,
            GiftItemEmojiRequest request
    ) {
        if (reaction.getEmoji() == request.emoji()) {
            giftItemEmojiReactionRepository.delete(reaction);
            return GiftItemEmojiResponse.of(giftItemId, null, HeartCardEmojiAction.DELETED, false);
        }

        reaction.updateEmoji(request.emoji());
        return GiftItemEmojiResponse.of(giftItemId, reaction.getEmoji(), HeartCardEmojiAction.UPDATED, false);
    }

    private void requireLogin(Long memberId) {
        if (memberId == null) {
            throw new CustomException(ErrorCode.COMMON_003);
        }
    }
}
