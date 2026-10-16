package com.sdp1617.backend.letter.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record CursorPageResponse<T>(
        @Schema(description = "이번 페이지 항목")
        List<T> items,
        @Schema(description = "다음 페이지 요청의 cursor. 마지막 페이지면 null")
        String nextCursor,
        @Schema(description = "다음 페이지가 있는지", example = "false")
        boolean hasNext
) {
}
