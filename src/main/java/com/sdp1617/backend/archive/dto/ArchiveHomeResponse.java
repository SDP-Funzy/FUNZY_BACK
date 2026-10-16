package com.sdp1617.backend.archive.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record ArchiveHomeResponse(
        @Schema(description = "아카이브 주인 회원 ID", example = "1")
        Long memberId,
        @Schema(description = "아카이브 주인 아이디", example = "tiki")
        String nickname,
        @Schema(description = "화면 타이틀", example = "아카이브")
        String title,
        @Schema(description = "주인 프로필 이미지 URL. 기본 이미지면 null")
        String profileImageUrl,
        @Schema(description = "모든 카테고리에 카드가 없는지 (빈 상태 화면)", example = "false")
        boolean empty,
        @Schema(description = "카테고리별 보드 (BOOK, MOVIE_TV, MUSIC, FASHION, PLACE, ETC 순서)")
        List<ArchiveCategorySectionResponse> sections
) {
}
