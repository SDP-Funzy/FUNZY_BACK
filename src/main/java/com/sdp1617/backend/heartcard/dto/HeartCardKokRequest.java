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
        ArchiveCategory category,

        @Schema(
                description = """
                        비공개(잠금) 여부. true면 아카이브에 넣지 않고 편지 보관함의 잠긴편지 필터로만 보입니다(본인만).
                        false면 공개로 아카이브에 보이고 친구도 봅니다. 생략하면 새로 콕할 때는 공개, 이미 콕한 카드는 그대로 둡니다.
                        """,
                example = "false"
        )
        Boolean locked
) {
    public HeartCardKokRequest(ArchiveCategory category) {
        this(true, category, null);
    }

    public HeartCardKokRequest(Boolean kok, ArchiveCategory category) {
        this(kok, category, null);
    }

    public boolean kokOrDefault() {
        return kok == null || kok;
    }

    /** 생략하면 카드 자기 카테고리 보드에 들어간다 (LB-110, #156). */
    public ArchiveCategory categoryOr(ArchiveCategory cardCategory) {
        return category == null ? cardCategory : category;
    }
}
