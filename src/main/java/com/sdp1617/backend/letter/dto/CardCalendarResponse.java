package com.sdp1617.backend.letter.dto;

import com.sdp1617.backend.letter.dto.CardBoxType;

import java.util.List;

public record CardCalendarResponse(
        CardBoxType type,
        int year,
        int month,
        List<CardCalendarDayResponse> days
) {
}
