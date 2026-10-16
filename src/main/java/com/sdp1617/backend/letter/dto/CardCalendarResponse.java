package com.sdp1617.backend.letter.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.sdp1617.backend.letter.dto.CardBoxType;

import java.util.List;

public record CardCalendarResponse(
        @Schema(description = "조회함 유형", example = "RECEIVED")
        CardBoxType type,
        @Schema(description = "연도", example = "2026")
        int year,
        @Schema(description = "월", example = "8")
        int month,
        @Schema(description = "카드를 주고받은 날짜들 (날짜순). 카드가 없는 날은 빠짐")
        List<CardCalendarDayResponse> days
) {
}
