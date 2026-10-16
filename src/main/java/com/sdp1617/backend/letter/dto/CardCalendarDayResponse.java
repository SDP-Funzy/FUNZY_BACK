package com.sdp1617.backend.letter.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

public record CardCalendarDayResponse(
        @Schema(description = "날짜", example = "2026-08-14")
        LocalDate date,
        @Schema(description = "그날 카드의 사진 URL (주고받은 순). 사진 없는 카드는 빠지므로 빈 목록일 수 있음")
        List<String> imageUrls
) {
}
