package com.sdp1617.backend.funzypack.controller;

import com.sdp1617.backend.funzypack.dto.FunzyPackCardListResponse;
import com.sdp1617.backend.funzypack.service.FunzyPackService;
import com.sdp1617.backend.global.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "펀지팩", description = "펀지팩 카드 목록 조회와 삭제 API")
public class FunzyPackController {

    private final FunzyPackService funzyPackService;

    @GetMapping("/api/funzy-packs/{packId}/cards")
    @Operation(
            summary = "펀지팩 카드 전체 조회",
            description = "펀지팩 전체 이미지 저장에 필요한 카드 목록을 카드 순서대로 조회합니다."
    )
    public ApiResponse<FunzyPackCardListResponse> getCards(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId,
            @Parameter(description = "펀지팩 ID. 현재 받은 편지 ID와 동일하게 사용합니다.", example = "6") @PathVariable Long packId
    ) {
        return ApiResponse.ok("펀지팩 카드 목록을 조회했습니다.", funzyPackService.getCards(memberId, packId));
    }

    @DeleteMapping("/api/funzy-packs/{packId}")
    @Operation(
            summary = "받은 펀지팩 삭제",
            description = "받은 펀지팩을 삭제합니다. 삭제 후 복구할 수 없으며, 포함된 마음카드의 아카이브 저장 상태와 이모지/문구 코멘트도 함께 정리합니다."
    )
    public ApiResponse<Void> deletePack(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId,
            @Parameter(description = "삭제할 펀지팩 ID. 현재 받은 편지 ID와 동일하게 사용합니다.", example = "6") @PathVariable Long packId
    ) {
        funzyPackService.deletePack(memberId, packId);
        return ApiResponse.ok("펀지팩을 삭제했습니다.", null);
    }
}
