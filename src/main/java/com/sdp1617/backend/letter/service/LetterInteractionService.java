package com.sdp1617.backend.letter.service;

import com.sdp1617.backend.global.error.CustomException;
import com.sdp1617.backend.global.error.ErrorCode;
import com.sdp1617.backend.letter.dto.LetterCommentRequest;
import com.sdp1617.backend.letter.dto.LetterInteractionResponse;
import com.sdp1617.backend.letter.entity.LetterInteraction;
import com.sdp1617.backend.letter.entity.LetterInteractionType;
import com.sdp1617.backend.letter.entity.LetterReactionType;
import com.sdp1617.backend.letter.repository.LetterInteractionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LetterInteractionService {

    private final LetterInteractionRepository letterInteractionRepository;
    private final ReceivedLetterAccess receivedLetterAccess;

    @Transactional
    public LetterInteractionResponse saveReaction(Long memberId, Long letterId, LetterReactionType reactionType) {
        requireLogin(memberId);
        validateReceivedLetterAccess(letterId, memberId);
        return saveOnce(letterId, memberId, LetterInteractionType.REACTION, reactionType.name());
    }

    @Transactional
    public LetterInteractionResponse saveComment(Long memberId, Long letterId, LetterCommentRequest request) {
        requireLogin(memberId);
        validateReceivedLetterAccess(letterId, memberId);
        LetterInteraction interaction = letterInteractionRepository.save(
                new LetterInteraction(letterId, memberId, LetterInteractionType.COMMENT, request.content())
        );
        return LetterInteractionResponse.from(interaction);
    }

    @Transactional
    public LetterInteractionResponse saveFavorite(Long memberId, Long letterId) {
        requireLogin(memberId);
        validateReceivedLetterAccess(letterId, memberId);
        return saveOnce(letterId, memberId, LetterInteractionType.FAVORITE, "FAVORITE");
    }

    private LetterInteractionResponse saveOnce(
            Long letterId,
            Long memberId,
            LetterInteractionType type,
        String value
    ) {
        if (letterInteractionRepository.existsByLetterIdAndMemberIdAndTypeAndValue(letterId, memberId, type, value)) {
            throw new CustomException(ErrorCode.COMMON_002);
        }

        LetterInteraction interaction = letterInteractionRepository.save(new LetterInteraction(letterId, memberId, type, value));
        return LetterInteractionResponse.from(interaction);
    }

    private void requireLogin(Long memberId) {
        if (memberId == null) {
            throw new CustomException(ErrorCode.COMMON_003);
        }
    }

    /** 받은 편지에만 반응할 수 있다. 편지를 잠가, 받은 편지를 지우며 반응을 정리하는 요청과 순서대로 처리한다. */
    private void validateReceivedLetterAccess(Long letterId, Long memberId) {
        receivedLetterAccess.lockReceivedLetter(memberId, letterId);
    }
}
