package com.sdp1617.backend.letter.dto;

import com.sdp1617.backend.letter.entity.Letter;
import com.sdp1617.backend.letter.entity.LetterStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/** 이어쓰기 목록의 편지 요약. */
public record LetterDraftResponse(
        Long letterId,
        @Schema(description = "DRAFT(작성 중) 또는 COMPLETED(완료, 전송 전)", example = "DRAFT")
        LetterStatus status,
        String toName,
        String fromName,
        @Schema(description = "작성한 카드 수 (0~5)", example = "2")
        int cardCount,
        boolean hasDoodlePick,
        LocalDateTime updatedAt
) {
    public static LetterDraftResponse from(Letter letter) {
        return new LetterDraftResponse(letter.getId(), letter.getStatus(), letter.getToName(), letter.getFromName(),
                letter.getCards().size(), letter.hasDoodlePick(), letter.getUpdatedAt());
    }
}
