package com.sdp1617.backend.archive.dto;

import com.sdp1617.backend.archive.entity.ArchiveCard;
import com.sdp1617.backend.archive.entity.ArchiveCategory;

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
        int likeCount,
        boolean liked,
        ArchiveVisibilityResponse visibility,
        LocalDateTime archivedAt
) {
    public static ArchiveCardDetailResponse from(ArchiveCard card, boolean maskPrivateFields, boolean liked) {
        boolean showSender = !maskPrivateFields || card.getVisibility().isSenderVisible();
        boolean showReceiver = !maskPrivateFields || card.getVisibility().isReceiverVisible();
        boolean showDate = !maskPrivateFields || card.getVisibility().isDateVisible();
        boolean showImage = !maskPrivateFields || card.getVisibility().isImageVisible();
        boolean showMessage = !maskPrivateFields || card.getVisibility().isMessageVisible();

        return new ArchiveCardDetailResponse(
                card.getId(),
                card.getLetterCardId(),
                card.getCategory(),
                showSender ? card.getSenderName() : null,
                showReceiver ? card.getReceiverName() : null,
                showDate ? card.getLetterDate() : null,
                showImage ? card.getImageUrl() : null,
                showMessage ? card.getMessage() : null,
                card.getLikeCount(),
                liked,
                ArchiveVisibilityResponse.from(card),
                card.getCreatedAt()
        );
    }
}
