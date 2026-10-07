package com.sdp1617.backend.letter.dto;

import com.sdp1617.backend.archive.entity.ArchiveCategory;
import com.sdp1617.backend.letter.entity.LetterCard;
import io.swagger.v3.oas.annotations.media.Schema;

public record LetterCardResponse(
        @Schema(description = "카드 ID (수정·삭제, 받는 쪽 이모지·코멘트·콕에 사용)", example = "10")
        Long cardId,
        @Schema(description = "편지 안 순서 (1부터)", example = "1")
        int cardOrder,
        ArchiveCategory category,
        @Schema(description = "기타 직접 입력 카테고리. 기타가 아니면 null")
        String customCategory,
        String title,
        String content,
        String link,
        String linkTitle,
        String imageUrl
) {
    public static LetterCardResponse from(LetterCard card) {
        return new LetterCardResponse(card.getId(), card.getCardOrder(), card.getCategory(), card.getCustomCategory(),
                card.getTitle(), card.getContent(), card.getLink(), card.getLinkTitle(), card.getImageUrl());
    }
}
