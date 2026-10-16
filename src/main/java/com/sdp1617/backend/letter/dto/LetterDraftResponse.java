package com.sdp1617.backend.letter.dto;

import com.sdp1617.backend.letter.entity.Letter;
import com.sdp1617.backend.letter.entity.LetterStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/** 이어쓰기 목록의 편지 요약. */
public record LetterDraftResponse(
        @Schema(description = "편지 ID", example = "1")
        Long letterId,
        @Schema(description = "DRAFT(작성 중) 또는 COMPLETED(완료, 전송 전)", example = "DRAFT")
        LetterStatus status,
        @Schema(description = "봉투의 받는 사람 이름", example = "은우")
        String toName,
        @Schema(description = "봉투의 보내는 사람 이름", example = "티키")
        String fromName,
        @Schema(description = "작성한 카드 수 (0~5)", example = "2")
        int cardCount,
        @Schema(description = "두들픽을 저장했는지", example = "false")
        boolean hasDoodlePick,
        @Schema(description = "마지막으로 고친 시각 (목록 정렬 기준)")
        LocalDateTime updatedAt
) {
    public static LetterDraftResponse from(Letter letter) {
        return new LetterDraftResponse(letter.getId(), letter.getStatus(), letter.getToName(), letter.getFromName(),
                letter.getCards().size(), letter.hasDoodlePick(), letter.getUpdatedAt());
    }
}
