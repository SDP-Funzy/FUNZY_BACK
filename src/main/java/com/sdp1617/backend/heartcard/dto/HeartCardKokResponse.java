package com.sdp1617.backend.heartcard.dto;

import com.sdp1617.backend.archive.entity.ArchiveCard;
import com.sdp1617.backend.archive.entity.ArchiveCategory;
import io.swagger.v3.oas.annotations.media.Schema;

public record HeartCardKokResponse(
        Long heartCardId,
        boolean kok,
        Long archiveCardId,
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
