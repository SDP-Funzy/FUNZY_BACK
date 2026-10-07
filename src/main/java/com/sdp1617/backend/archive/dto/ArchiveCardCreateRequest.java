package com.sdp1617.backend.archive.dto;

import com.sdp1617.backend.archive.entity.ArchiveCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "받은 편지의 마음카드를 아카이브에 담을 때 필요한 요청 값")
public record ArchiveCardCreateRequest(
        @Schema(description = "받은 편지의 마음카드 ID (편지 열기 응답의 cards[].cardId)", example = "10", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull Long letterCardId,

        @Schema(
                description = "카드가 저장될 아카이브 카테고리. 미분류 카드는 ETC를 사용합니다.",
                example = "BOOK",
                requiredMode = Schema.RequiredMode.REQUIRED,
                allowableValues = {"BOOK", "MOVIE_TV", "MUSIC", "FASHION", "PLACE", "ETC"}
        )
        @NotNull ArchiveCategory category
) {
}
