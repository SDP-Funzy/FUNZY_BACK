package com.sdp1617.backend.archive.dto;

import com.sdp1617.backend.archive.entity.ArchiveCard;
import com.sdp1617.backend.archive.entity.ArchiveCategory;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record ArchiveCardDetailResponse(
        Long archiveCardId,
        Long letterCardId,
        ArchiveCategory category,
        String senderName,
        String receiverName,
        LocalDate letterDate,
        String imageUrl,
        String message,
        @Schema(description = "비공개(잠금) 여부. 비공개 카드는 주인만 상세를 볼 수 있습니다", example = "false")
        boolean locked,
        @Schema(description = "댓글 수 (답글 포함)", example = "3")
        long commentCount,
        LocalDateTime archivedAt
) {
    public static ArchiveCardDetailResponse from(ArchiveCard card, long commentCount) {
        return new ArchiveCardDetailResponse(
                card.getId(),
                card.getLetterCardId(),
                card.getCategory(),
                card.getSenderName(),
                card.getReceiverName(),
                card.getLetterDate(),
                card.getImageUrl(),
                card.getMessage(),
                card.isLocked(),
                commentCount,
                card.getCreatedAt()
        );
    }
}
