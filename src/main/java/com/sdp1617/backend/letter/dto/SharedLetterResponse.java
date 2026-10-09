package com.sdp1617.backend.letter.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 공유 링크로 연 편지 (#87, #114). 누구나 읽기 전용으로 볼 수 있고, viewer로 화면을 나눈다. */
public record SharedLetterResponse(
        @Schema(description = "편지 내용 (카드·두들픽 포함). 보낸 사람·받는 사람이 아니면 recipient·readAt·selectedGiftItemId는 null로 가린다")
        LetterResponse letter,
        @Schema(description = """
                보는 사람과 편지의 관계
                - ANONYMOUS: 로그인하지 않음 → 반응·선물 고르기는 로그인 유도
                - SENDER: 보낸 사람 본인 → 미리보기
                - RECIPIENT: 받는 사람으로 확정된 회원 → 반응·선물 고르기 가능 (받은 편지함에서 지웠으면 receivable=true, 받기로 되돌림)
                - OTHER: 로그인했지만 받는 사람이 아님 → receivable이면 '받기'(receive), 아니면 읽기만
                """, example = "OTHER")
        Viewer viewer,
        @Schema(description = "receive를 호출할 수 있는지: 아직 받는 사람이 없는 편지를 로그인한 회원이 연 경우, 또는 받은 편지함에서 지운 받는 사람이 연 경우", example = "true")
        boolean receivable
) {
    public enum Viewer {
        ANONYMOUS, SENDER, RECIPIENT, OTHER
    }
}
