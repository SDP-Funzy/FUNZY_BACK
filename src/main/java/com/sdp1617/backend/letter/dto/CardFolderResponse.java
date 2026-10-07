package com.sdp1617.backend.letter.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/** 마음카드 보관함의 상대방별 폴더. */
public record CardFolderResponse(
        @Schema(description = "상대 회원 ID", example = "2")
        Long memberId,
        @Schema(description = "상대 닉네임")
        String nickname,
        @Schema(description = "주고받은 카드 수", example = "3")
        long cardCount,
        @Schema(description = "가장 최근 카드의 사진 URL. 사진 있는 카드가 없으면 null")
        String latestImageUrl,
        @Schema(description = "가장 최근 카드를 주고받은 시각")
        LocalDateTime latestSentAt
) {
}
