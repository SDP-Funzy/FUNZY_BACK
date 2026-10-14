package com.sdp1617.backend.archive.dto;

import com.sdp1617.backend.archive.entity.ArchiveCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "받은 편지의 마음카드를 아카이브에 담을 때 필요한 요청 값")
public record ArchiveCardCreateRequest(
        @Schema(description = "받은 편지의 마음카드 ID (편지 열기 응답의 cards[].cardId)", example = "10", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull Long letterCardId,

        @Schema(
                description = "카드가 저장될 아카이브 카테고리. 생략하면 카드의 카테고리로 저장합니다 (직접 입력 카테고리 카드는 ETC).",
                example = "BOOK",
                allowableValues = {"BOOK", "MOVIE_TV", "MUSIC", "FASHION", "PLACE", "ETC"}
        )
        ArchiveCategory category
) {
    /** 생략하면 카드 자기 카테고리 보드에 들어간다 (콕과 같은 기준, #156). */
    public ArchiveCategory categoryOr(ArchiveCategory cardCategory) {
        return category == null ? cardCategory : category;
    }
}
