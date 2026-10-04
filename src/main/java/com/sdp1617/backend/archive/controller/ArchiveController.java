package com.sdp1617.backend.archive.controller;

import com.sdp1617.backend.archive.dto.ArchiveCardCreateRequest;
import com.sdp1617.backend.archive.dto.ArchiveCardDetailResponse;
import com.sdp1617.backend.archive.dto.ArchiveHomeResponse;
import com.sdp1617.backend.archive.dto.ArchiveLikeResponse;
import com.sdp1617.backend.archive.dto.ArchiveVisibilityResponse;
import com.sdp1617.backend.archive.dto.ArchiveVisibilityUpdateRequest;
import com.sdp1617.backend.archive.service.ArchiveService;
import com.sdp1617.backend.global.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "아카이브", description = "찜한 편지 카드를 카테고리별로 저장하고 조회하는 아카이브 API")
public class ArchiveController {

    private final ArchiveService archiveService;

    @GetMapping("/api/archive/home")
    @Operation(
            summary = "아카이브 메인홈 조회",
            description = "앱 진입 시 기본으로 노출할 아카이브 홈 데이터를 조회합니다. 프로필 이미지, '아카이브' 타이틀, 카테고리별 카드 목록과 전체 빈 상태 여부를 반환합니다."
    )
    public ApiResponse<ArchiveHomeResponse> getHome(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId
    ) {
        return ApiResponse.ok("아카이브 홈을 조회했습니다.", archiveService.getHome(memberId));
    }

    @PostMapping("/api/archive/cards")
    @Operation(
            summary = "편지 카드 아카이브 저장",
            description = "편지 보관함에서 찜한 카드를 아카이브에 저장합니다. 같은 회원이 같은 편지 카드를 중복 저장할 수 없습니다."
    )
    public ApiResponse<ArchiveCardDetailResponse> saveCard(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId,
            @Valid @RequestBody ArchiveCardCreateRequest request
    ) {
        return ApiResponse.created("아카이브에 카드를 저장했습니다.", archiveService.saveCard(memberId, request));
    }

    @GetMapping("/api/archive/cards/{archiveCardId}")
    @Operation(
            summary = "아카이브 카드 상세 조회",
            description = """
                    카드 발신자, 수신자, 날짜, 대표 이미지, 메시지를 포함한 상세 정보를 조회합니다.
                    - 내 카드는 모든 항목을 조회합니다.
                    - 친구의 카드는 비공개로 설정된 항목이 null로 내려갑니다. 어떤 항목이 비공개인지는 visibility로 알 수 있습니다.
                    - 내 카드도 친구의 카드도 아니면 존재하지 않는 카드와 같이 ARCHIVE_002입니다.
                    """
    )
    public ApiResponse<ArchiveCardDetailResponse> getCard(
            @Parameter(hidden = true) @AuthenticationPrincipal Long viewerMemberId,
            @Parameter(description = "조회할 아카이브 카드 ID", example = "1") @PathVariable Long archiveCardId
    ) {
        return ApiResponse.ok("아카이브 카드를 조회했습니다.", archiveService.getCard(viewerMemberId, archiveCardId));
    }

    @DeleteMapping("/api/archive/cards/{archiveCardId}")
    @Operation(
            summary = "아카이브 카드 삭제",
            description = "카드를 아카이브에서만 제거합니다. 편지 보관함 원본은 삭제하지 않습니다."
    )
    public ApiResponse<Void> deleteCard(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId,
            @Parameter(description = "삭제할 아카이브 카드 ID", example = "1") @PathVariable Long archiveCardId
    ) {
        archiveService.deleteCard(memberId, archiveCardId);
        return ApiResponse.ok("아카이브 카드를 삭제했습니다.", null);
    }

    @PatchMapping("/api/archive/cards/{archiveCardId}/visibility")
    @Operation(
            summary = "카드 공개 범위 수정",
            description = "카드 발신자, 수신자, 날짜, 이미지, 메시지 5개 항목의 공개 여부를 각각 저장합니다. 본인 아카이브에서는 비공개 항목도 볼 수 있고, 친구 조회에서는 비공개 항목이 숨겨집니다."
    )
    public ApiResponse<ArchiveVisibilityResponse> updateVisibility(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId,
            @Parameter(description = "공개 범위를 수정할 아카이브 카드 ID", example = "1") @PathVariable Long archiveCardId,
            @Valid @RequestBody ArchiveVisibilityUpdateRequest request
    ) {
        return ApiResponse.ok("카드 공개 범위를 수정했습니다.", archiveService.updateVisibility(memberId, archiveCardId, request));
    }

    @PostMapping("/api/archive/cards/{archiveCardId}/like")
    @Operation(
            summary = "친구 아카이브 카드 좋아요",
            description = """
                    친구의 아카이브 카드 좋아요를 토글합니다. 누르지 않은 카드면 좋아요, 이미 누른 카드면 취소합니다.
                    - 본인 카드에는 좋아요를 누를 수 없습니다(ARCHIVE_003).
                    - 친구가 아니면 좋아요를 누를 수 없습니다(ARCHIVE_002). 이미 누른 좋아요는 친구를 끊은 뒤에도 취소할 수 있습니다.
                    """
    )
    public ApiResponse<ArchiveLikeResponse> toggleLike(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId,
            @Parameter(description = "좋아요를 누를 아카이브 카드 ID", example = "1") @PathVariable Long archiveCardId
    ) {
        return ApiResponse.ok("좋아요 상태를 변경했습니다.", archiveService.toggleLike(memberId, archiveCardId));
    }
}
