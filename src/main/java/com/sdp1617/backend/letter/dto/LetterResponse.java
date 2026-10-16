package com.sdp1617.backend.letter.dto;

import com.sdp1617.backend.letter.entity.DesignType;
import com.sdp1617.backend.letter.entity.Letter;
import com.sdp1617.backend.letter.entity.LetterStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record LetterResponse(
        @Schema(description = "편지 ID", example = "1")
        Long letterId,
        @Schema(description = "상태: DRAFT(작성 중) / COMPLETED(완료, 전송 전) / SENT(전송됨)", example = "DRAFT")
        LetterStatus status,
        @Schema(description = "보낸 회원")
        LetterMemberResponse sender,
        @Schema(description = "받는 회원. 보내기 전이면 null")
        LetterMemberResponse recipient,
        @Schema(description = "봉투의 받는 사람 이름", example = "은우")
        String toName,
        @Schema(description = "봉투의 보내는 사람 이름", example = "티키")
        String fromName,
        @Schema(description = "봉투 디자인", example = "DesignType_A")
        DesignType designType,
        @Schema(description = "카드 목록 (카드 순서대로, 최대 5장)")
        List<LetterCardResponse> cards,
        @Schema(description = "두들픽. 없으면 null")
        DoodlePickResponse doodlePick,
        @Schema(description = "받는 사람이 고른 선물 후보 ID. 안 골랐으면 null")
        Long selectedGiftItemId,
        @Schema(description = "편지를 만든 시각")
        LocalDateTime createdAt,
        @Schema(description = "마지막으로 고친 시각")
        LocalDateTime updatedAt,
        @Schema(description = "완료한 시각. 완료 전이면 null")
        LocalDateTime completedAt,
        @Schema(description = "보낸 시각. 보내기 전이면 null. 공유 링크로 보낸 편지는 받는 사람이 받은 시각 (아직 아무도 안 받았으면 링크를 만든 시각)")
        LocalDateTime sentAt,
        @Schema(description = "받는 사람이 처음 열어본 시각. 안 읽었으면 null")
        LocalDateTime readAt
) {
    /**
     * 공유 링크를 보낸 사람·받는 사람이 아닌 사람(로그인 안 함 포함)에게 보여줄 때: 받는 사람이 누구인지, 읽었는지,
     * 어떤 선물을 골랐는지는 가린다 (#87). 링크는 다른 사람에게 다시 전달될 수 있다.
     */
    public static LetterResponse forLinkViewer(Letter letter) {
        LetterResponse full = from(letter);
        return new LetterResponse(full.letterId(), full.status(), full.sender(), null, full.toName(), full.fromName(),
                full.designType(), full.cards(), full.doodlePick(), null, full.createdAt(), full.updatedAt(),
                full.completedAt(), full.sentAt(), null);
    }

    public static LetterResponse from(Letter letter) {
        return from(letter, Map.of());
    }

    /** 받는 사람이 볼 때: 카드마다 내 콕 상태(카드 ID → 콕 상태)를 함께 내린다 (#147). */
    public static LetterResponse from(Letter letter, Map<Long, LetterCardKokResponse> kokByCardId) {
        return new LetterResponse(letter.getId(), letter.getStatus(),
                LetterMemberResponse.from(letter.getSender()), LetterMemberResponse.from(letter.getRecipient()),
                letter.getToName(), letter.getFromName(),
                letter.getDesignType(),
                letter.getCards().stream().map(card -> LetterCardResponse.from(card, kokByCardId.get(card.getId()))).toList(),
                DoodlePickResponse.from(letter), letter.getSelectedGiftItemId(), letter.getCreatedAt(), letter.getUpdatedAt(),
                letter.getCompletedAt(), letter.getSentAt(), letter.getReadAt());
    }
}
