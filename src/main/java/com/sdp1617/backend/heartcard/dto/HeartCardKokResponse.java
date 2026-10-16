package com.sdp1617.backend.heartcard.dto;

import com.sdp1617.backend.archive.entity.ArchiveCard;
import com.sdp1617.backend.archive.entity.ArchiveCategory;
import io.swagger.v3.oas.annotations.media.Schema;

public record HeartCardKokResponse(
        @Schema(description = "카드 ID (편지 카드 ID)", example = "10")
        Long heartCardId,
        @Schema(description = "콕했는지", example = "true")
        boolean kok,
        @Schema(description = "콕으로 만든 아카이브 카드 ID. 콕하지 않았으면 null", example = "1")
        Long archiveCardId,
        @Schema(description = "저장한 카테고리. 콕하지 않았으면 null", example = "MUSIC")
        ArchiveCategory category,
        @Schema(description = "비공개(잠금) 여부. 콕하지 않은 카드면 null", example = "false")
        Boolean locked
) {
    public static HeartCardKokResponse active(Long heartCardId, ArchiveCard archiveCard) {
        return new HeartCardKokResponse(heartCardId, true, archiveCard.getId(), archiveCard.getCategory(),
                archiveCard.isLocked());
    }

    public static HeartCardKokResponse inactive(Long heartCardId) {
        return new HeartCardKokResponse(heartCardId, false, null, null, null);
    }
}
