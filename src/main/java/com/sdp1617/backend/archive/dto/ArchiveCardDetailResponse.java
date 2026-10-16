package com.sdp1617.backend.archive.dto;

import com.sdp1617.backend.archive.entity.ArchiveCard;
import com.sdp1617.backend.archive.entity.ArchiveCategory;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record ArchiveCardDetailResponse(
        @Schema(description = "아카이브 카드 ID (댓글 API에 사용)", example = "1")
        Long archiveCardId,
        @Schema(description = "원래 편지 카드 ID (콕 API의 heartCardId)", example = "10")
        Long letterCardId,
        @Schema(description = "저장한 카테고리", example = "MUSIC")
        ArchiveCategory category,
        @Schema(description = "봉투의 보내는 사람 이름 (콕한 시점)", example = "티키")
        String senderName,
        @Schema(description = "봉투의 받는 사람 이름 (콕한 시점)", example = "은우")
        String receiverName,
        @Schema(description = "편지를 받은 날짜", example = "2026-08-14")
        LocalDate letterDate,
        @Schema(description = "카드 사진 URL. 없으면 null")
        String imageUrl,
        @Schema(description = "카드 본문", example = "출근길마다 듣는데 네 생각이 났어")
        String message,
        @Schema(description = "비공개(잠금) 여부. 비공개 카드는 주인만 상세를 볼 수 있습니다", example = "false")
        boolean locked,
        @Schema(description = "댓글 수 (답글 포함)", example = "3")
        long commentCount,
        @Schema(description = "콕한 시각")
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
