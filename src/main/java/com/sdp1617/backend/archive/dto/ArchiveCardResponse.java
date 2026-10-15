package com.sdp1617.backend.archive.dto;

import com.sdp1617.backend.archive.entity.ArchiveCard;
import com.sdp1617.backend.archive.entity.ArchiveCategory;

import java.time.LocalDateTime;

public record ArchiveCardResponse(
        Long archiveCardId,
        Long letterCardId,
        ArchiveCategory category,
        String imageUrl,
        String messagePreview,
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
