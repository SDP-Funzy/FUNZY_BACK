package com.sdp1617.backend.archive.controller;

import com.sdp1617.backend.archive.dto.ArchiveCardDetailResponse;
import com.sdp1617.backend.archive.dto.ArchiveHomeResponse;
import com.sdp1617.backend.archive.service.ArchiveService;
import com.sdp1617.backend.global.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "아카이브", description = "공개로 콕한 마음카드를 카테고리별 보드로 보는 아카이브 API. 친구도 볼 수 있습니다. 카드를 담고 빼는 것은 콕 API(PUT /api/heart-cards/{heartCardId}/kok, heartCardId = letterCardId)로만 합니다. 비공개(잠금) 카드는 아카이브에 나오지 않고, 공개/비공개 전환도 콕 API의 locked로 합니다.")
public class ArchiveController {

    private final ArchiveService archiveService;

    @GetMapping("/api/archive/home")
    @Operation(
            summary = "아카이브 메인홈 조회",
            description = "앱 진입 시 기본으로 노출할 아카이브 홈 데이터를 조회합니다. 닉네임, 프로필 이미지, '아카이브' 타이틀, 카테고리별 카드 목록과 전체 빈 상태 여부를 반환합니다. 비공개(잠금) 카드는 나오지 않습니다. 카테고리 보드의 대표 사진은 sections[].thumbnailImageUrl(처음 저장된 카드 중 사진 있는 카드, 없으면 null)입니다."
    )
    public ApiResponse<ArchiveHomeResponse> getHome(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId
    ) {
        return ApiResponse.ok("아카이브 홈을 조회했습니다.", archiveService.getHome(memberId));
    }

    @GetMapping("/api/archive/friends/{friendMemberId}/home")
    @Operation(
            summary = "친구 아카이브 홈 조회",
            description = """
                    친구(맞팔)의 아카이브를 읽기 전용으로 조회합니다. 탭(카테고리) 구조는 내 아카이브 홈과 같습니다.
                    - 친구가 비공개로 콕한 카드는 나오지 않습니다. 카드 상세는 아카이브 카드 상세 조회 API를 사용합니다.
                    - 친구가 아니면 SOCIAL_007입니다. friendMemberId는 친구 목록 API의 memberId입니다.
                    """
    )
    public ApiResponse<ArchiveHomeResponse> getFriendHome(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId,
            @Parameter(description = "아카이브를 볼 친구의 회원 ID", example = "2") @PathVariable Long friendMemberId
    ) {
        return ApiResponse.ok("친구 아카이브 홈을 조회했습니다.", archiveService.getFriendHome(memberId, friendMemberId));
    }

    @GetMapping("/api/archive/cards/{archiveCardId}")
    @Operation(
            summary = "아카이브 카드 상세 조회",
            description = """
                    카드 발신자, 수신자, 날짜, 대표 이미지, 메시지를 포함한 상세 정보를 조회합니다.
                    - 내 카드는 공개·비공개(locked) 모두 조회합니다.
                    - 친구의 카드는 공개 카드만 조회합니다. 비공개 카드면 존재하지 않는 카드와 같이 ARCHIVE_002입니다.
                    - 내 카드도 친구의 카드도 아니면 ARCHIVE_002입니다.
                    """
    )
    public ApiResponse<ArchiveCardDetailResponse> getCard(
            @Parameter(hidden = true) @AuthenticationPrincipal Long viewerMemberId,
            @Parameter(description = "조회할 아카이브 카드 ID", example = "1") @PathVariable Long archiveCardId
    ) {
        return ApiResponse.ok("아카이브 카드를 조회했습니다.", archiveService.getCard(viewerMemberId, archiveCardId));
    }
}
