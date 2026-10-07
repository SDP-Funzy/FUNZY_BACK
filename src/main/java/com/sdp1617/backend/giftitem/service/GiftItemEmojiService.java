package com.sdp1617.backend.giftitem.service;

import com.sdp1617.backend.giftitem.dto.GiftItemEmojiRequest;
import com.sdp1617.backend.giftitem.dto.GiftItemEmojiResponse;
import com.sdp1617.backend.giftitem.entity.GiftItemEmojiReaction;
import com.sdp1617.backend.giftitem.repository.GiftItemEmojiReactionRepository;
import com.sdp1617.backend.global.error.CustomException;
import com.sdp1617.backend.global.error.ErrorCode;
import com.sdp1617.backend.letter.entity.GiftItem;
import com.sdp1617.backend.letter.service.ReceivedLetterAccess;
import com.sdp1617.backend.heartcard.entity.HeartCardEmojiAction;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GiftItemEmojiService {

    private final GiftItemEmojiReactionRepository giftItemEmojiReactionRepository;
    private final ReceivedLetterAccess receivedLetterAccess;

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
                .orElseGet(() -> create(memberId, giftItem.getId(), request));
    }

    private GiftItemEmojiResponse create(Long memberId, Long giftItemId, GiftItemEmojiRequest request) {
        GiftItemEmojiReaction reaction = giftItemEmojiReactionRepository.save(
                new GiftItemEmojiReaction(giftItemId, memberId, request.emoji())
        );
        // TODO: 알림 도메인이 연결되면 최초 등록 시에만 보낸 사람 알림 INBOX 생성 호출.
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
