package com.sdp1617.backend.letter.service;

import com.sdp1617.backend.archive.entity.ArchiveCard;
import com.sdp1617.backend.archive.repository.ArchiveCardLikeRepository;
import com.sdp1617.backend.archive.repository.ArchiveCardRepository;
import com.sdp1617.backend.giftitem.repository.GiftItemEmojiReactionRepository;
import com.sdp1617.backend.heartcard.repository.HeartCardEmojiReactionRepository;
import com.sdp1617.backend.heartcard.repository.HeartCardPhraseCommentRepository;
import com.sdp1617.backend.letter.entity.GiftItem;
import com.sdp1617.backend.letter.entity.Letter;
import com.sdp1617.backend.letter.entity.LetterCard;
import com.sdp1617.backend.letter.repository.LetterInteractionRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 받는 사람이 받은 편지를 지울 때 그 편지에 남긴 내 반응을 함께 지운다 (LR-512: 복구 불가, 아카이브·이모지·코멘트까지 삭제).
 * 콕으로 담은 아카이브 카드와 거기 달린 다른 사람의 좋아요, 카드 이모지·문구 코멘트, 선물 이모지, 편지 리액션·댓글·찜.
 * 보낸 사람의 편지와 고른 선물 기록은 남긴다.
 */
@Component
@RequiredArgsConstructor
public class ReceivedLetterReactionCleaner {

    private final ArchiveCardRepository archiveCardRepository;
    private final ArchiveCardLikeRepository archiveCardLikeRepository;
    private final HeartCardEmojiReactionRepository heartCardEmojiReactionRepository;
    private final HeartCardPhraseCommentRepository heartCardPhraseCommentRepository;
    private final GiftItemEmojiReactionRepository giftItemEmojiReactionRepository;
    private final LetterInteractionRepository letterInteractionRepository;

    /** 편지를 잠근 트랜잭션 안에서 호출한다 (반응을 다는 요청과 순서대로 처리되도록). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void cleanMyReactions(Long memberId, Letter letter) {
        List<Long> cardIds = letter.getCards().stream().map(LetterCard::getId).toList();
        List<Long> giftItemIds = letter.getGiftItems().stream().map(GiftItem::getId).toList();

        if (!cardIds.isEmpty()) {
            List<Long> archiveCardIds = archiveCardRepository.findByOwnerMemberIdAndLetterCardIdIn(memberId, cardIds)
                    .stream().map(ArchiveCard::getId).toList();
            if (!archiveCardIds.isEmpty()) {
                archiveCardLikeRepository.deleteByArchiveCardIdIn(archiveCardIds);
                archiveCardRepository.deleteAllById(archiveCardIds);
            }
            heartCardEmojiReactionRepository.deleteByMemberIdAndHeartCardIdIn(memberId, cardIds);
            heartCardPhraseCommentRepository.deleteByMemberIdAndHeartCardIdIn(memberId, cardIds);
        }
        if (!giftItemIds.isEmpty()) {
            giftItemEmojiReactionRepository.deleteByMemberIdAndGiftItemIdIn(memberId, giftItemIds);
        }
        letterInteractionRepository.deleteByLetterIdAndMemberId(letter.getId(), memberId);
    }
}
