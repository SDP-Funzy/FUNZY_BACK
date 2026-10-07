package com.sdp1617.backend.heartcard.controller;

import com.sdp1617.backend.global.common.response.ApiResponse;
import com.sdp1617.backend.heartcard.dto.HeartCardKokRequest;
import com.sdp1617.backend.heartcard.dto.HeartCardKokResponse;
import com.sdp1617.backend.heartcard.service.HeartCardKokService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "마음카드 콕", description = "마음카드 콕 상태 조회와 토글 API")
public class HeartCardKokController {

    private final HeartCardKokService heartCardKokService;

    @GetMapping("/api/heart-cards/{heartCardId}/kok")
    @Operation(
            summary = "마음카드 콕 상태 조회",
            description = "카드 열람 시 현재 로그인 사용자의 콕 상태를 조회합니다. 콕한 카드는 archiveCardId가 함께 반환됩니다."
    )
    public ApiResponse<HeartCardKokResponse> getKok(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId,
            @Parameter(description = "받은 편지의 카드 ID (편지 열기 응답의 cards[].cardId). 받은 편지가 아니면 COMMON_001", example = "10") @PathVariable Long heartCardId
    ) {
        return ApiResponse.ok("콕 상태를 조회했습니다.", heartCardKokService.getKok(memberId, heartCardId));
    }

    @PutMapping("/api/heart-cards/{heartCardId}/kok")
    @Operation(
            summary = "마음카드 콕 상태 변경",
            description = """
                    요청한 kok 값으로 콕 상태를 맞춥니다. 같은 요청을 반복해도 결과가 같습니다.
                    - kok=true(생략 시 기본값): 아카이브에 저장합니다. 이미 콕한 카드면 그대로 둡니다.
                    - kok=false: 아카이브에서 즉시 제거합니다. 콕하지 않은 카드면 그대로 둡니다.
                    - category를 생략하면 ETC로 저장합니다.
                    """
    )
    public ApiResponse<HeartCardKokResponse> updateKok(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId,
            @Parameter(description = "받은 편지의 카드 ID (편지 열기 응답의 cards[].cardId). 받은 편지가 아니면 COMMON_001", example = "10") @PathVariable Long heartCardId,
            @RequestBody(required = false) HeartCardKokRequest request
    ) {
        return ApiResponse.ok("콕 상태를 변경했습니다.", heartCardKokService.updateKok(memberId, heartCardId, request));
    }
}
