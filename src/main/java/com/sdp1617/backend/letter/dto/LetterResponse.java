package com.sdp1617.backend.letter.dto;

import com.sdp1617.backend.card.dto.DesignType;
import com.sdp1617.backend.letter.entity.Letter;
import com.sdp1617.backend.letter.entity.LetterStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

public record LetterResponse(
        @Schema(description = "편지 ID", example = "1")
        Long letterId,
        @Schema(description = "상태: DRAFT(작성 중) / COMPLETED(완료, 전송 전) / SENT(전송됨)", example = "DRAFT")
        LetterStatus status,
        String toName,
        String fromName,
        DesignType designType,
        @Schema(description = "카드 목록 (카드 순서대로, 최대 5장)")
        List<LetterCardResponse> cards,
        @Schema(description = "두들픽. 없으면 null")
        DoodlePickResponse doodlePick,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime completedAt,
        LocalDateTime sentAt
) {
    public static LetterResponse from(Letter letter) {
        return new LetterResponse(letter.getId(), letter.getStatus(), letter.getToName(), letter.getFromName(),
                letter.getDesignType(), letter.getCards().stream().map(LetterCardResponse::from).toList(),
                DoodlePickResponse.from(letter), letter.getCreatedAt(), letter.getUpdatedAt(),
                letter.getCompletedAt(), letter.getSentAt());
    }
}
