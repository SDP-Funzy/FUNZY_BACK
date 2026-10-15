package com.sdp1617.backend.letter.dto;

import com.sdp1617.backend.archive.entity.ArchiveCategory;
import com.sdp1617.backend.letter.entity.LetterCard;
import io.swagger.v3.oas.annotations.media.Schema;

public record LetterCardResponse(
        @Schema(description = "카드 ID (수정·삭제, 받는 쪽 콕에 사용)", example = "10")
        Long cardId,
        @Schema(description = "편지 안 순서 (1부터)", example = "1")
        int cardOrder,
        ArchiveCategory category,
        @Schema(description = "기타 직접 입력 카테고리. 기타가 아니면 null")
        String customCategory,
        String content,
        @Schema(description = "링크 (없으면 null). http:// 또는 https://로 시작하는 주소만 저장됨. 화면에 <a href>로 그릴 때도 http(s)인지 한 번 더 확인해 주세요")
        String link,
        String linkTitle,
        String imageUrl,
        @Schema(description = "내 콕 상태. 받는 사람이 편지 열기·선물 고르기로 볼 때만 있고, 콕하지 않은 카드나 받는 사람이 아니면 null (#147)")
        LetterCardKokResponse kok
) {
    public static LetterCardResponse from(LetterCard card) {
        return from(card, null);
    }

    public static LetterCardResponse from(LetterCard card, LetterCardKokResponse kok) {
        return new LetterCardResponse(card.getId(), card.getCardOrder(), card.getCategory(), card.getCustomCategory(),
                card.getContent(), card.getLink(), card.getLinkTitle(), card.getImageUrl(), kok);
    }
}
