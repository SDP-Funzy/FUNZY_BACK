package com.sdp1617.backend.heartcard.service;

import com.sdp1617.backend.global.error.CustomException;
import com.sdp1617.backend.global.error.ErrorCode;
import com.sdp1617.backend.heartcard.dto.HeartCardEmojiOptionListResponse;
import com.sdp1617.backend.heartcard.dto.HeartCardEmojiRequest;
import com.sdp1617.backend.heartcard.dto.HeartCardEmojiResponse;
import com.sdp1617.backend.heartcard.entity.HeartCardEmojiAction;
import com.sdp1617.backend.heartcard.entity.HeartCardEmojiReaction;
import com.sdp1617.backend.heartcard.repository.HeartCardEmojiReactionRepository;
import com.sdp1617.backend.letter.service.ReceivedLetterAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HeartCardEmojiService {

    private final HeartCardEmojiReactionRepository heartCardEmojiReactionRepository;
    private final ReceivedLetterAccess receivedLetterAccess;

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
        receivedLetterAccess.lockReceivedCard(memberId, heartCardId);
        return heartCardEmojiReactionRepository.findByHeartCardIdAndMemberId(heartCardId, memberId)
                .map(reaction -> updateOrDelete(heartCardId, reaction, request))
                .orElseGet(() -> create(memberId, heartCardId, request));
    }

    private HeartCardEmojiResponse create(Long memberId, Long heartCardId, HeartCardEmojiRequest request) {
        HeartCardEmojiReaction reaction = heartCardEmojiReactionRepository.save(
                new HeartCardEmojiReaction(heartCardId, memberId, request.emoji())
        );
        // TODO: 알림 도메인(LN-021)이 연결되면 최초 등록 시에만 알림 INBOX 생성 호출.
        return HeartCardEmojiResponse.of(reaction.getHeartCardId(), reaction.getEmoji(), HeartCardEmojiAction.CREATED, false);
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
