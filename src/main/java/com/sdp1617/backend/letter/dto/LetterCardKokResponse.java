package com.sdp1617.backend.letter.dto;

import com.sdp1617.backend.archive.entity.ArchiveCard;
import com.sdp1617.backend.archive.entity.ArchiveCategory;
import io.swagger.v3.oas.annotations.media.Schema;

/** 받는 사람이 편지를 열 때 카드마다 보여줄 내 콕 상태 (#147). */
public record LetterCardKokResponse(
        @Schema(description = "콕으로 만든 아카이브 카드 ID", example = "1")
        Long archiveCardId,
        @Schema(description = "아카이브 카테고리", example = "MUSIC")
        ArchiveCategory category,
        @Schema(description = "비공개(잠금) 여부. true면 아카이브에 안 보이고 잠긴편지 필터로만 보임", example = "false")
        boolean locked
) {
    public static LetterCardKokResponse from(ArchiveCard archiveCard) {
        return new LetterCardKokResponse(archiveCard.getId(), archiveCard.getCategory(), archiveCard.isLocked());
    }
}
