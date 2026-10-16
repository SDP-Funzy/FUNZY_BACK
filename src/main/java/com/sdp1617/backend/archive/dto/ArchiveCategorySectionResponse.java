package com.sdp1617.backend.archive.dto;

import com.sdp1617.backend.archive.entity.ArchiveCategory;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Objects;

public record ArchiveCategorySectionResponse(
        @Schema(description = "카테고리", example = "MUSIC")
        ArchiveCategory category,
        @Schema(description = "카테고리 화면 이름", example = "음악")
        String displayName,
        @Schema(description = "보드 대표 사진 (LB-012): 이 카테고리에 처음 저장된 카드 중 사진이 있는 카드의 사진. 사진 있는 카드가 없으면 null",
                example = "https://example.com/cards/1.jpg")
        String thumbnailImageUrl,
        @Schema(description = "카드 목록 (최근에 저장한 순)")
        List<ArchiveCardResponse> cards,
        @Schema(description = "이 카테고리에 카드가 없는지", example = "false")
        boolean empty
) {
    /** cards는 최근에 저장한 순이라, 대표 사진은 뒤에서부터 처음 만나는 사진이다. */
    public static ArchiveCategorySectionResponse of(ArchiveCategory category, List<ArchiveCardResponse> cards) {
        String thumbnail = cards.reversed().stream()
                .map(ArchiveCardResponse::imageUrl)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
        return new ArchiveCategorySectionResponse(category, category.getDisplayName(), thumbnail, cards, cards.isEmpty());
    }
}
