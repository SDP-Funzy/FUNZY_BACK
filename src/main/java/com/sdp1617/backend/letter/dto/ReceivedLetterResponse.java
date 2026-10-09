package com.sdp1617.backend.letter.dto;

import com.sdp1617.backend.letter.entity.Letter;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/** 받은 편지함의 편지 요약. */
public record ReceivedLetterResponse(
        Long letterId,
        @Schema(description = "보낸 회원")
        LetterMemberResponse sender,
        @Schema(description = "봉투의 보내는 사람 이름", example = "티키")
        String senderName,
        @Schema(description = "봉투의 받는 사람 이름", example = "은우")
        String receiverName,
        @Schema(description = "받은 시각. 직접 받은 편지는 보낸 시각, 공유 링크로 받은 편지는 받기를 누른 시각")
        LocalDateTime receivedAt,
        @Schema(description = "카드 수", example = "3")
        int heartCardCount,
        @Schema(description = "읽었는지. false면 미읽음 표시")
        boolean read
) {
    public static ReceivedLetterResponse from(Letter letter) {
        return new ReceivedLetterResponse(letter.getId(), LetterMemberResponse.from(letter.getSender()),
                letter.getFromName(), letter.getToName(), letter.getSentAt(), letter.getCards().size(),
                letter.getReadAt() != null);
    }
}
