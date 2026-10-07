package com.sdp1617.backend.letter.controller;

import com.sdp1617.backend.global.common.response.ApiResponse;
import com.sdp1617.backend.letter.dto.LetterCommentRequest;
import com.sdp1617.backend.letter.dto.LetterInteractionResponse;
import com.sdp1617.backend.letter.entity.LetterReactionType;
import com.sdp1617.backend.letter.service.LetterInteractionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "편지 ③ 편지 리액션·댓글·찜", description = "받은 펀지(편지 전체)에 리액션·댓글·찜을 남기는 API. 받은 사람만 사용할 수 있고, 비로그인 사용자는 호출할 수 없습니다. 카드 단위 반응은 \"편지 ③ 마음카드 …\" API입니다.")
public class LetterInteractionController {

    private final LetterInteractionService letterInteractionService;

    @PostMapping("/api/letters/{letterId}/reactions")
    @Operation(
            summary = "편지 리액션 저장",
            description = "편지에 리액션을 저장합니다. Authorization 헤더가 없으면 401이 반환되며, 프론트에서 로그인/회원가입 유도 팝업을 표시하면 됩니다."
    )
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<LetterInteractionResponse> saveReaction(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId,
            @Parameter(description = "반응을 남길 받은 편지 ID. 받은 편지가 아니면 COMMON_001", example = "1") @PathVariable Long letterId,
            @Parameter(description = "리액션 종류", example = "HEART") @RequestParam LetterReactionType reactionType
    ) {
        return ApiResponse.created(
                "리액션을 저장했습니다.",
                letterInteractionService.saveReaction(memberId, letterId, reactionType)
        );
    }

    @PostMapping("/api/letters/{letterId}/comments")
    @Operation(
            summary = "편지 댓글 저장",
            description = "편지에 댓글을 저장합니다. 저장 실패 시 에러 응답이 내려가며, 프론트는 입력 내용을 유지하고 재시도 안내를 표시합니다."
    )
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<LetterInteractionResponse> saveComment(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId,
            @Parameter(description = "댓글을 남길 받은 편지 ID. 받은 편지가 아니면 COMMON_001", example = "1") @PathVariable Long letterId,
            @Valid @RequestBody LetterCommentRequest request
    ) {
        return ApiResponse.created(
                "댓글을 저장했습니다.",
                letterInteractionService.saveComment(memberId, letterId, request)
        );
    }

    @PostMapping("/api/letters/{letterId}/favorite")
    @Operation(
            summary = "편지 찜 저장",
            description = "편지를 찜합니다. 비로그인 사용자는 호출할 수 없고, 중복 찜은 한 번만 저장됩니다."
    )
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<LetterInteractionResponse> saveFavorite(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId,
            @Parameter(description = "찜할 받은 편지 ID. 받은 편지가 아니면 COMMON_001", example = "1") @PathVariable Long letterId
    ) {
        return ApiResponse.created(
                "편지를 찜했습니다.",
                letterInteractionService.saveFavorite(memberId, letterId)
        );
    }
}
