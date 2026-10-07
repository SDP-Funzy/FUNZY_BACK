package com.sdp1617.backend.letter.service;

import com.sdp1617.backend.global.error.CustomException;
import com.sdp1617.backend.global.error.ErrorCode;
import com.sdp1617.backend.letter.entity.GiftItem;
import com.sdp1617.backend.letter.entity.Letter;
import com.sdp1617.backend.letter.entity.LetterCard;
import com.sdp1617.backend.letter.repository.GiftItemRepository;
import com.sdp1617.backend.letter.repository.LetterCardRepository;
import com.sdp1617.backend.letter.repository.LetterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 받는 사람 반응(이모지·문구 코멘트·콕·선물 이모지·편지 리액션)의 권한 확인을 한 곳에 모은다 (#82).
 * - 반응 달기: 그 편지를 받은 사람이고, 받은 편지함에서 지우지 않았어야 한다.
 *   편지 행을 잠가, 받은 편지를 지우면서 반응을 정리하는 요청과 순서대로 처리되게 한다
 *   (안 잠그면 정리가 끝난 뒤에 반응이 새로 생길 수 있다).
 * - 반응 보기: 받은 사람 또는 보낸 사람.
 * 권한이 없으면 존재 여부를 드러내지 않도록 없는 리소스(COMMON_001)와 같이 응답한다.
 */
@Component
@RequiredArgsConstructor
public class ReceivedLetterAccess {

    private final LetterRepository letterRepository;
    private final LetterCardRepository letterCardRepository;
    private final GiftItemRepository giftItemRepository;

    /** 반응을 달 카드. 편지 행을 잠근다. */
    @Transactional(propagation = Propagation.MANDATORY)
    public LetterCard lockReceivedCard(Long memberId, Long cardId) {
        Long letterId = letterCardRepository.findLetterIdById(cardId).orElseThrow(this::notFound);
        lockReceivedLetter(memberId, letterId);
        return letterCardRepository.findWithLetterById(cardId).orElseThrow(this::notFound);
    }

    /** 내 반응을 볼 카드 (받은 사람만). */
    public LetterCard requireReceivedCard(Long memberId, Long cardId) {
        LetterCard card = letterCardRepository.findWithLetterById(cardId).orElseThrow(this::notFound);
        if (!card.getLetter().isVisibleToRecipient(memberId)) {
            throw notFound();
        }
        return card;
    }

    /** 반응 목록을 볼 카드 (받은 사람 또는 보낸 사람). */
    public LetterCard requireReadableCard(Long memberId, Long cardId) {
        LetterCard card = letterCardRepository.findWithLetterById(cardId).orElseThrow(this::notFound);
        Letter letter = card.getLetter();
        if (!letter.isVisibleToRecipient(memberId) && !letter.isWrittenBy(memberId)) {
            throw notFound();
        }
        return card;
    }

    /** 반응을 달 선물 후보. 편지 행을 잠근다. */
    @Transactional(propagation = Propagation.MANDATORY)
    public GiftItem lockReceivedGiftItem(Long memberId, Long giftItemId) {
        Long letterId = giftItemRepository.findLetterIdById(giftItemId).orElseThrow(this::notFound);
        lockReceivedLetter(memberId, letterId);
        return giftItemRepository.findWithLetterById(giftItemId).orElseThrow(this::notFound);
    }

    /** 내 반응을 볼 선물 후보 (받은 사람만). */
    public GiftItem requireReceivedGiftItem(Long memberId, Long giftItemId) {
        GiftItem giftItem = giftItemRepository.findWithLetterById(giftItemId).orElseThrow(this::notFound);
        if (!giftItem.getLetter().isVisibleToRecipient(memberId)) {
            throw notFound();
        }
        return giftItem;
    }

    /** 반응을 달 편지. 편지 행을 잠근다. */
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
