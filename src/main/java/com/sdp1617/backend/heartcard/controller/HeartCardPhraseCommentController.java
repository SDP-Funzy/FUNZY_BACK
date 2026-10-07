package com.sdp1617.backend.heartcard.controller;

import com.sdp1617.backend.global.common.response.ApiResponse;
import com.sdp1617.backend.heartcard.dto.HeartCardPhraseCommentCreateRequest;
import com.sdp1617.backend.heartcard.dto.HeartCardPhraseCommentListResponse;
import com.sdp1617.backend.heartcard.dto.HeartCardPhraseCommentResponse;
import com.sdp1617.backend.heartcard.dto.HeartCardPhraseCommentUpdateRequest;
import com.sdp1617.backend.heartcard.service.HeartCardPhraseCommentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "편지 ③ 마음카드 문구 코멘트", description = "받은 펀지의 마음카드 본문 중 선택한 문구에 코멘트를 남기는 API. 작성·수정·삭제는 받은 사람, 조회는 받은 사람과 보낸 사람이 할 수 있습니다. heartCardId는 편지 열기 응답의 cards[].cardId입니다.")
public class HeartCardPhraseCommentController {

    private final HeartCardPhraseCommentService heartCardPhraseCommentService;

    @GetMapping("/api/heart-cards/{heartCardId}/phrase-comments")
    @Operation(
            summary = "마음카드 문구 코멘트 목록 조회",
            description = "카드 열람 시 형광색 표시할 코멘트 문구 범위와 코멘트 내용을 조회합니다."
    )
    public ApiResponse<HeartCardPhraseCommentListResponse> getComments(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId,
            @Parameter(description = "받은 편지의 카드 ID (편지 열기 응답의 cards[].cardId). 받은 편지가 아니면 COMMON_001", example = "10") @PathVariable Long heartCardId
    ) {
        return ApiResponse.ok("문구 코멘트 목록을 조회했습니다.", heartCardPhraseCommentService.getComments(memberId, heartCardId));
    }

    @PostMapping("/api/heart-cards/{heartCardId}/phrase-comments")
    @Operation(
            summary = "마음카드 문구 코멘트 작성",
            description = "선택한 문구 범위에 최대 50자의 코멘트를 저장합니다. 기존 코멘트 영역과 겹치면 저장할 수 없습니다. 등록 시에만 알림 생성 대상입니다."
    )
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<HeartCardPhraseCommentResponse> createComment(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId,
            @Parameter(description = "받은 편지의 카드 ID (편지 열기 응답의 cards[].cardId). 받은 편지가 아니면 COMMON_001", example = "10") @PathVariable Long heartCardId,
            @Valid @RequestBody HeartCardPhraseCommentCreateRequest request
    ) {
        return ApiResponse.created("문구 코멘트를 작성했습니다.", heartCardPhraseCommentService.createComment(memberId, heartCardId, request));
    }

    @GetMapping("/api/heart-cards/{heartCardId}/phrase-comments/{commentId}")
    @Operation(
            summary = "마음카드 문구 코멘트 상세 조회",
            description = "형광색 문구를 탭했을 때 해당 문구에 연결된 코멘트 내용을 조회합니다."
    )
    public ApiResponse<HeartCardPhraseCommentResponse> getComment(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId,
            @Parameter(description = "받은 편지의 카드 ID (편지 열기 응답의 cards[].cardId). 받은 편지가 아니면 COMMON_001", example = "10") @PathVariable Long heartCardId,
            @Parameter(description = "문구 코멘트 ID", example = "1") @PathVariable Long commentId
    ) {
        return ApiResponse.ok("문구 코멘트를 조회했습니다.", heartCardPhraseCommentService.getComment(memberId, heartCardId, commentId));
    }

    @PatchMapping("/api/heart-cards/{heartCardId}/phrase-comments/{commentId}")
    @Operation(
            summary = "마음카드 문구 코멘트 수정",
            description = "본인이 작성한 문구 코멘트 내용을 수정합니다. 수정 시 알림은 생성하지 않습니다."
    )
    public ApiResponse<HeartCardPhraseCommentResponse> updateComment(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId,
            @Parameter(description = "받은 편지의 카드 ID (편지 열기 응답의 cards[].cardId). 받은 편지가 아니면 COMMON_001", example = "10") @PathVariable Long heartCardId,
            @Parameter(description = "문구 코멘트 ID", example = "1") @PathVariable Long commentId,
            @Valid @RequestBody HeartCardPhraseCommentUpdateRequest request
    ) {
        return ApiResponse.ok("문구 코멘트를 수정했습니다.", heartCardPhraseCommentService.updateComment(memberId, heartCardId, commentId, request));
    }

    @DeleteMapping("/api/heart-cards/{heartCardId}/phrase-comments/{commentId}")
    @Operation(
            summary = "마음카드 문구 코멘트 삭제",
            description = "본인이 작성한 문구 코멘트를 삭제합니다. 삭제 후 프론트는 해당 문구의 형광색 표시를 제거하면 됩니다."
    )
    public ApiResponse<Void> deleteComment(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId,
            @Parameter(description = "받은 편지의 카드 ID (편지 열기 응답의 cards[].cardId). 받은 편지가 아니면 COMMON_001", example = "10") @PathVariable Long heartCardId,
            @Parameter(description = "문구 코멘트 ID", example = "1") @PathVariable Long commentId
    ) {
        heartCardPhraseCommentService.deleteComment(memberId, heartCardId, commentId);
        return ApiResponse.ok("문구 코멘트를 삭제했습니다.", null);
    }
}
