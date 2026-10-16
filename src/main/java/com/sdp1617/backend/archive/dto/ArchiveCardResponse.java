package com.sdp1617.backend.archive.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.sdp1617.backend.archive.entity.ArchiveCard;
import com.sdp1617.backend.archive.entity.ArchiveCategory;

import java.time.LocalDateTime;

public record ArchiveCardResponse(
        @Schema(description = "아카이브 카드 ID (상세 조회에 사용)", example = "1")
        Long archiveCardId,
        @Schema(description = "원래 편지 카드 ID (콕 API의 heartCardId)", example = "10")
        Long letterCardId,
        @Schema(description = "저장한 카테고리", example = "MUSIC")
        ArchiveCategory category,
        @Schema(description = "카드 사진 URL. 없으면 null")
        String imageUrl,
        @Schema(description = "본문 앞 40자", example = "출근길마다 듣는데 네 생각이 났어")
        String messagePreview,
        @Schema(description = "콕한 시각")
        LocalDateTime archivedAt
) {
    public static ArchiveCardResponse from(ArchiveCard card) {
        return new ArchiveCardResponse(
                card.getId(),
                card.getLetterCardId(),
                card.getCategory(),
                card.getImageUrl(),
                preview(card.getMessage()),
                card.getCreatedAt()
        );
    }

    private static String preview(String message) {
        if (message == null || message.length() <= 40) {
            return message;
        }
        return message.substring(0, 40);
    }
}
