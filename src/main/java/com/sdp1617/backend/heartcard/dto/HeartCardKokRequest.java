package com.sdp1617.backend.heartcard.dto;

import com.sdp1617.backend.archive.entity.ArchiveCategory;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "마음카드 콕 상태 변경 요청")
public record HeartCardKokRequest(
        @Schema(description = "바꿀 콕 상태. true면 아카이브에 저장, false면 아카이브에서 제거합니다. 생략하면 true입니다.", example = "true")
        Boolean kok,

        @Schema(
                description = "저장할 아카이브 카테고리. 생략하면 카드의 카테고리로 저장합니다 (직접 입력 카테고리 카드는 ETC).",
                example = "BOOK",
                allowableValues = {"BOOK", "MOVIE_TV", "MUSIC", "FASHION", "PLACE", "ETC"}
        )
        ArchiveCategory category
) {
    public HeartCardKokRequest(ArchiveCategory category) {
        this(true, category);
    }

    public boolean kokOrDefault() {
        return kok == null || kok;
    }

    /** 생략하면 카드 자기 카테고리 보드에 들어간다 (LB-110, #156). */
    public ArchiveCategory categoryOr(ArchiveCategory cardCategory) {
        return category == null ? cardCategory : category;
    }
}
