package com.sdp1617.backend.letter.dto;

import com.sdp1617.backend.archive.entity.ArchiveCategory;
import com.sdp1617.backend.letter.entity.DesignType;
import com.sdp1617.backend.letter.entity.Letter;
import com.sdp1617.backend.letter.entity.LetterCard;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/** 마음카드 보관함의 카드 1장. 카드는 주고받은 편지 속 카드다 (#82). */
public record CardStorageResponse(
        @Schema(description = "카드 ID (받은 카드면 이모지·코멘트·콕 API에 사용)", example = "10")
        Long cardId,
        @Schema(description = "카드가 들어 있는 편지 ID", example = "1")
        Long letterId,
        Long senderId,
        @Schema(description = "보낸 회원 닉네임")
        String senderNickname,
        Long receiverId,
        @Schema(description = "받은 회원 닉네임")
        String receiverNickname,
        @Schema(description = "봉투의 보내는 사람 이름", example = "티키")
        String fromName,
        @Schema(description = "봉투의 받는 사람 이름", example = "은우")
        String toName,
        DesignType designType,
        ArchiveCategory category,
        @Schema(description = "기타 직접 입력 카테고리. 기타가 아니면 null")
        String customCategory,
        String link,
        String linkTitle,
        String content,
        String imageUrl,
        @Schema(description = "편지를 보낸(받은) 시각")
        LocalDateTime sentAt
) {
    public static CardStorageResponse from(LetterCard card) {
        Letter letter = card.getLetter();
        return new CardStorageResponse(
                card.getId(),
                letter.getId(),
                letter.getSender().getId(),
                letter.getSender().getNickname(),
                letter.getRecipient().getId(),
                letter.getRecipient().getNickname(),
                letter.getFromName(),
                letter.getToName(),
                letter.getDesignType(),
                card.getCategory(),
                card.getCustomCategory(),
                card.getLink(),
                card.getLinkTitle(),
                card.getContent(),
                card.getImageUrl(),
                letter.getSentAt()
        );
    }
}
