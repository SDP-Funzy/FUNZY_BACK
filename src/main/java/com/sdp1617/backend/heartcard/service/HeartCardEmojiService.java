package com.sdp1617.backend.heartcard.service;

import com.sdp1617.backend.global.error.CustomException;
import com.sdp1617.backend.global.error.ErrorCode;
import com.sdp1617.backend.heartcard.dto.HeartCardEmojiOptionListResponse;
import com.sdp1617.backend.heartcard.dto.HeartCardEmojiRequest;
import com.sdp1617.backend.heartcard.dto.HeartCardEmojiResponse;
import com.sdp1617.backend.heartcard.entity.HeartCardEmojiAction;
import com.sdp1617.backend.heartcard.entity.HeartCardEmojiReaction;
import com.sdp1617.backend.heartcard.repository.HeartCardEmojiReactionRepository;
import com.sdp1617.backend.letter.entity.LetterCard;
import com.sdp1617.backend.letter.service.LetterReactedEvent;
import com.sdp1617.backend.letter.service.ReceivedLetterAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HeartCardEmojiService {

    private final HeartCardEmojiReactionRepository heartCardEmojiReactionRepository;
    private final ReceivedLetterAccess receivedLetterAccess;
    private final ApplicationEventPublisher eventPublisher;

    public HeartCardEmojiOptionListResponse getEmojiOptions() {
        return HeartCardEmojiOptionListResponse.fromDefaultOptions();
    }

    public HeartCardEmojiResponse getMyEmoji(Long memberId, Long heartCardId) {
        requireLogin(memberId);
        receivedLetterAccess.requireReceivedCard(memberId, heartCardId);
        return heartCardEmojiReactionRepository.findByHeartCardIdAndMemberId(heartCardId, memberId)
                .map(HeartCardEmojiResponse::from)
                .orElseGet(() -> HeartCardEmojiResponse.empty(heartCardId));
    }

    @Transactional
    public HeartCardEmojiResponse updateEmoji(Long memberId, Long heartCardId, HeartCardEmojiRequest request) {
        requireLogin(memberId);
        LetterCard heartCard = receivedLetterAccess.lockReceivedCard(memberId, heartCardId);
        return heartCardEmojiReactionRepository.findByHeartCardIdAndMemberId(heartCardId, memberId)
                .map(reaction -> updateOrDelete(heartCardId, reaction, request))
                .orElseGet(() -> create(memberId, heartCard, request));
    }

    /** 남길 때 보낸 사람에게 알림 (LR-114). 바꾸거나 지울 때는 알림 없고, 지웠다 다시 남겨도 카드마다 한 번만 (알림 쪽에서 거름). */
    private HeartCardEmojiResponse create(Long memberId, LetterCard heartCard, HeartCardEmojiRequest request) {
        HeartCardEmojiReaction reaction = heartCardEmojiReactionRepository.save(
                new HeartCardEmojiReaction(heartCard.getId(), memberId, request.emoji())
        );
        LetterReactedEvent event =
                LetterReactedEvent.of(LetterReactedEvent.Kind.CARD_EMOJI, heartCard.getLetter(), heartCard.getId());
        eventPublisher.publishEvent(event);
        return HeartCardEmojiResponse.of(reaction.getHeartCardId(), reaction.getEmoji(), HeartCardEmojiAction.CREATED,
                event.notificationCreated());
    }

    private HeartCardEmojiResponse updateOrDelete(
            Long heartCardId,
            HeartCardEmojiReaction reaction,
            HeartCardEmojiRequest request
    ) {
        if (reaction.getEmoji() == request.emoji()) {
            heartCardEmojiReactionRepository.delete(reaction);
            return HeartCardEmojiResponse.of(heartCardId, null, HeartCardEmojiAction.DELETED, false);
        }

        reaction.updateEmoji(request.emoji());
        return HeartCardEmojiResponse.of(heartCardId, reaction.getEmoji(), HeartCardEmojiAction.UPDATED, false);
    }

    private void requireLogin(Long memberId) {
        if (memberId == null) {
            throw new CustomException(ErrorCode.COMMON_003);
        }
    }
}
