package com.sdp1617.backend.letter.dto;

import com.sdp1617.backend.letter.entity.Letter;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

/** 보낸 편지함의 편지 요약. */
public record SentLetterResponse(
        Long letterId,
        @Schema(description = "받는 회원")
        LetterMemberResponse recipient,
        @Schema(description = "봉투의 받는 사람 이름", example = "은우")
        String toName,
        @Schema(description = "봉투의 보내는 사람 이름", example = "티키")
        String fromName,
        @Schema(description = "카드 수", example = "3")
        int cardCount,
        @Schema(description = "목록 썸네일용 카드 사진 URL. 카드 순서대로 사진이 있는 카드 최대 3장, 사진이 없으면 빈 목록 (#147)")
        List<String> thumbnailImageUrls,
        LocalDateTime sentAt,
        @Schema(description = "받는 사람이 읽었는지")
        boolean read
) {
    private static final int THUMBNAIL_COUNT = 3;

    public static SentLetterResponse from(Letter letter) {
        return new SentLetterResponse(letter.getId(), LetterMemberResponse.from(letter.getRecipient()),
                letter.getToName(), letter.getFromName(), letter.getCards().size(),
                letter.thumbnailImageUrls(THUMBNAIL_COUNT), letter.getSentAt(),
                letter.getReadAt() != null);
    }
}
