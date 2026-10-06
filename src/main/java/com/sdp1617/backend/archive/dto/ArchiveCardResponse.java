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
    /** 주인이 보는 카드. 비공개 항목도 모두 보인다. */
    public static ArchiveCardResponse from(ArchiveCard card) {
        return from(card, false);
    }

    /** maskPrivateFields가 true면(친구가 보는 경우) 주인이 비공개로 설정한 이미지·메시지를 null로 내린다. */
    public static ArchiveCardResponse from(ArchiveCard card, boolean maskPrivateFields) {
        boolean showImage = !maskPrivateFields || card.getVisibility().isImageVisible();
        boolean showMessage = !maskPrivateFields || card.getVisibility().isMessageVisible();
        return new ArchiveCardResponse(
                card.getId(),
                card.getLetterCardId(),
                card.getCategory(),
                showImage ? card.getImageUrl() : null,
                showMessage ? preview(card.getMessage()) : null,
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
