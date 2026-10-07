package com.sdp1617.backend.heartcard.controller;

import com.sdp1617.backend.global.common.response.ApiResponse;
import com.sdp1617.backend.heartcard.dto.HeartCardEmojiOptionListResponse;
import com.sdp1617.backend.heartcard.dto.HeartCardEmojiRequest;
import com.sdp1617.backend.heartcard.dto.HeartCardEmojiResponse;
import com.sdp1617.backend.heartcard.service.HeartCardEmojiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "편지 ③ 마음카드 이모지", description = "받은 펀지의 마음카드에 이모지를 남기는 API. 이모지 남기기·내 이모지 조회는 받은 사람만, 이모지 선택지 조회는 누구나 사용할 수 있습니다. heartCardId는 편지 열기 응답의 cards[].cardId입니다.")
public class HeartCardEmojiController {

    private final HeartCardEmojiService heartCardEmojiService;

    @GetMapping("/api/heart-cards/emojis")
    @Operation(
            summary = "기본 이모지 선택지 조회",
            description = "이모지 선택 팝업에 표시할 기본 이모지 6개를 조회합니다."
    )
    public ApiResponse<HeartCardEmojiOptionListResponse> getEmojiOptions() {
        return ApiResponse.ok("이모지 선택지를 조회했습니다.", heartCardEmojiService.getEmojiOptions());
    }

    @GetMapping("/api/heart-cards/{heartCardId}/emoji")
    @Operation(
            summary = "내 마음카드 이모지 상태 조회",
            description = "열람 중인 마음카드에 현재 로그인 사용자가 남긴 이모지를 조회합니다. 없으면 reacted=false, emoji=null로 반환합니다."
    )
    public ApiResponse<HeartCardEmojiResponse> getMyEmoji(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId,
            @Parameter(description = "받은 편지의 카드 ID (편지 열기 응답의 cards[].cardId). 받은 편지가 아니면 COMMON_001", example = "10") @PathVariable Long heartCardId
    ) {
        return ApiResponse.ok("마음카드 이모지를 조회했습니다.", heartCardEmojiService.getMyEmoji(memberId, heartCardId));
    }

    @PutMapping("/api/heart-cards/{heartCardId}/emoji")
    @Operation(
            summary = "마음카드 이모지 등록/수정/삭제",
            description = "마음카드에 이모지가 없으면 등록, 기존 이모지와 다른 값을 선택하면 수정, 같은 이모지를 다시 선택하면 삭제합니다. 최초 등록 시에만 알림 생성 대상입니다."
    )
    public ApiResponse<HeartCardEmojiResponse> updateEmoji(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId,
            @Parameter(description = "받은 편지의 카드 ID (편지 열기 응답의 cards[].cardId). 받은 편지가 아니면 COMMON_001", example = "10") @PathVariable Long heartCardId,
            @Valid @RequestBody HeartCardEmojiRequest request
    ) {
        return ApiResponse.ok("마음카드 이모지를 변경했습니다.", heartCardEmojiService.updateEmoji(memberId, heartCardId, request));
    }
}
