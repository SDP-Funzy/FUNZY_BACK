package com.sdp1617.backend.giftitem.controller;

import com.sdp1617.backend.giftitem.dto.GiftItemEmojiRequest;
import com.sdp1617.backend.giftitem.dto.GiftItemEmojiResponse;
import com.sdp1617.backend.giftitem.service.GiftItemEmojiService;
import com.sdp1617.backend.global.common.response.ApiResponse;
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
@Tag(name = "선물 항목 이모지", description = "두들픽 선물 항목 이모지 상태 조회, 등록, 수정, 삭제 API")
public class GiftItemEmojiController {

    private final GiftItemEmojiService giftItemEmojiService;

    @GetMapping("/api/gift-items/{giftItemId}/emoji")
    @Operation(
            summary = "선물 항목 이모지 상태 조회",
            description = "선물 항목에 현재 로그인 사용자가 남긴 이모지를 조회합니다. 없으면 reacted=false, emoji=null로 반환합니다."
    )
    public ApiResponse<GiftItemEmojiResponse> getMyEmoji(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId,
            @Parameter(description = "선물 항목 ID", example = "200") @PathVariable Long giftItemId
    ) {
        return ApiResponse.ok("선물 항목 이모지를 조회했습니다.", giftItemEmojiService.getMyEmoji(memberId, giftItemId));
    }

    @PutMapping("/api/gift-items/{giftItemId}/emoji")
    @Operation(
            summary = "선물 항목 이모지 등록/수정/삭제",
            description = "이모지가 없으면 등록, 기존 이모지와 다른 값을 선택하면 수정, 같은 이모지를 다시 선택하면 삭제합니다. 최초 등록 시에만 알림 생성 대상입니다."
    )
    public ApiResponse<GiftItemEmojiResponse> updateEmoji(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId,
            @Parameter(description = "선물 항목 ID", example = "200") @PathVariable Long giftItemId,
            @Valid @RequestBody GiftItemEmojiRequest request
    ) {
        return ApiResponse.ok("선물 항목 이모지를 변경했습니다.", giftItemEmojiService.updateEmoji(memberId, giftItemId, request));
    }
}
