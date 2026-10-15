package com.sdp1617.backend.letter.service;

import com.sdp1617.backend.global.error.CustomException;
import com.sdp1617.backend.global.error.ErrorCode;
import com.sdp1617.backend.letter.entity.Letter;
import com.sdp1617.backend.letter.entity.LetterCard;
import com.sdp1617.backend.letter.repository.LetterCardRepository;
import com.sdp1617.backend.letter.repository.LetterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 받는 사람 동작(콕·선물 고르기)의 권한 확인을 한 곳에 모은다 (#82).
 * - 콕·선물 고르기: 그 편지를 받은 사람이고, 받은 편지함에서 지우지 않았어야 한다.
 *   편지 행을 잠가, 받은 편지를 지우면서 아카이브 카드를 정리하는 요청과 순서대로 처리되게 한다
 *   (안 잠그면 정리가 끝난 뒤에 아카이브 카드가 새로 생길 수 있다).
 * 권한이 없으면 존재 여부를 드러내지 않도록 없는 리소스(COMMON_001)와 같이 응답한다.
 */
@Component
@RequiredArgsConstructor
public class ReceivedLetterAccess {

    private final LetterRepository letterRepository;
    private final LetterCardRepository letterCardRepository;

    /** 콕할 카드. 편지 행을 잠근다. */
    @Transactional(propagation = Propagation.MANDATORY)
    public LetterCard lockReceivedCard(Long memberId, Long cardId) {
        Long letterId = letterCardRepository.findLetterIdById(cardId).orElseThrow(this::notFound);
        lockReceivedLetter(memberId, letterId);
        return letterCardRepository.findWithLetterById(cardId).orElseThrow(this::notFound);
    }

    /** 콕 상태를 볼 카드 (받은 사람만). */
    public LetterCard requireReceivedCard(Long memberId, Long cardId) {
        LetterCard card = letterCardRepository.findWithLetterById(cardId).orElseThrow(this::notFound);
        if (!card.getLetter().isVisibleToRecipient(memberId)) {
            throw notFound();
        }
        return card;
    }

    /** 받은 편지. 편지 행을 잠근다. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Letter lockReceivedLetter(Long memberId, Long letterId) {
        return letterRepository.findByIdForUpdate(letterId)
                .filter(letter -> letter.isVisibleToRecipient(memberId))
                .orElseThrow(this::notFound);
    }

    private CustomException notFound() {
        return new CustomException(ErrorCode.COMMON_001);
    }
}
