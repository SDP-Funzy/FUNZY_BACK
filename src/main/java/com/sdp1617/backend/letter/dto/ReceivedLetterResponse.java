package com.sdp1617.backend.letter.dto;

import com.sdp1617.backend.letter.entity.Letter;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

/** 받은 편지함의 편지 요약. */
public record ReceivedLetterResponse(
        Long letterId,
        @Schema(description = "보낸 회원")
        LetterMemberResponse sender,
        @Schema(description = "봉투의 보내는 사람 이름", example = "티키")
        String senderName,
        @Schema(description = "봉투의 받는 사람 이름", example = "은우")
        String receiverName,
        @Schema(description = "받은 시각 (보낸 시각)")
        LocalDateTime receivedAt,
        @Schema(description = "카드 수", example = "3")
        int heartCardCount,
        @Schema(description = "목록 썸네일용 카드 사진 URL. 카드 순서대로 사진이 있는 카드 최대 3장, 사진이 없으면 빈 목록 (#147)")
        List<String> thumbnailImageUrls,
        @Schema(description = "읽었는지. false면 미읽음 표시")
        boolean read
) {
    private static final int THUMBNAIL_COUNT = 3;

    public static ReceivedLetterResponse from(Letter letter) {
        return new ReceivedLetterResponse(letter.getId(), LetterMemberResponse.from(letter.getSender()),
                letter.getFromName(), letter.getToName(), letter.getSentAt(), letter.getCards().size(),
                letter.thumbnailImageUrls(THUMBNAIL_COUNT), letter.getReadAt() != null);
    }
}
