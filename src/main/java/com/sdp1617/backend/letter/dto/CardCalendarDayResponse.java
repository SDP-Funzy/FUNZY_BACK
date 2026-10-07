package com.sdp1617.backend.letter.dto;

import java.time.LocalDate;
import java.util.List;

public record CardCalendarDayResponse(
        LocalDate date,
        List<String> imageUrls
) {
}
