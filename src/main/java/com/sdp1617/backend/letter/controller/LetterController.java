package com.sdp1617.backend.letter.controller;

import com.sdp1617.backend.global.common.response.ApiResponse;
import com.sdp1617.backend.letter.dto.DoodlePickRequest;
import com.sdp1617.backend.letter.dto.LetterCardRequest;
import com.sdp1617.backend.letter.dto.LetterDraftResponse;
import com.sdp1617.backend.letter.dto.LetterEnvelopeRequest;
import com.sdp1617.backend.letter.dto.LetterResponse;
import com.sdp1617.backend.letter.service.LetterCardContentResolver;
import com.sdp1617.backend.letter.service.LetterWriteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/letters")
@RequiredArgsConstructor
@Tag(name = "편지 쓰기", description = "편지(펀지) 작성: 봉투 구성 → 카드 1~5장 → 두들픽 → 완료. 보내기 전까지는 보낸 사람만 수정할 수 있습니다. 편지 조회·삭제·보내기는 '편지함' API를 사용합니다.")
public class LetterController {

    private final LetterWriteService letterWriteService;
    private final LetterCardContentResolver letterCardContentResolver;

    @PostMapping
    @Operation(summary = "편지 쓰기 시작", description = """
            받는 사람·보내는 사람 이름(각 10자 이내)과 봉투 디자인으로 새 편지를 만듭니다 (LW-111/112). 상태는 DRAFT(작성 중)입니다.
            - 이어서 카드 추가 API로 카드를 1~5장 작성합니다.
            """)
    public ApiResponse<LetterResponse> start(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId,
            @Valid @RequestBody LetterEnvelopeRequest request
    ) {
        return ApiResponse.created("편지를 만들었습니다.", letterWriteService.start(memberId, request));
    }

    @GetMapping("/drafts")
    @Operation(summary = "보내지 않은 편지 목록 (이어쓰기)", description = """
            아직 보내지 않은 내 편지(DRAFT 작성 중, COMPLETED 완료)를 최근 수정 순으로 조회합니다 (LW-012 이어쓰기).
            """)
    public ApiResponse<List<LetterDraftResponse>> getUnsentLetters(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId
    ) {
        return ApiResponse.ok("보내지 않은 편지 목록을 조회했습니다.", letterWriteService.getUnsentLetters(memberId));
    }

    @PutMapping("/{letterId}")
    @Operation(summary = "봉투 수정", description = """
            받는 사람·보내는 사람 이름과 봉투 디자인을 수정합니다. 전송된 편지는 수정할 수 없습니다(LETTER_003).
            """)
    public ApiResponse<LetterResponse> updateEnvelope(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId,
            @Parameter(description = "편지 ID", example = "1") @PathVariable Long letterId,
            @Valid @RequestBody LetterEnvelopeRequest request
    ) {
        return ApiResponse.ok("봉투를 수정했습니다.", letterWriteService.updateEnvelope(memberId, letterId, request));
    }

    @PostMapping("/{letterId}/cards")
    @Operation(summary = "카드 추가", description = """
            편지에 카드를 추가합니다. 편지당 최대 5장이며(LETTER_002), 작성 순서대로 번호가 매겨집니다 (LW-015/016, LW-210~513).
            - 사진은 마음카드 이미지 presigned URL(POST /api/cards/images/presigned-url)로 업로드를 마친 imageKey를 넣습니다.
              업로드가 끝나지 않았으면 CARD_002, 지원하지 않는 형식이면 CARD_003, 5MB 초과면 CARD_004, 내 imageKey가 아니면 COMMON_004입니다.
            - 전송된 편지에는 추가할 수 없습니다(LETTER_003).
            """)
    public ApiResponse<LetterResponse> addCard(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId,
            @Parameter(description = "편지 ID", example = "1") @PathVariable Long letterId,
            @Valid @RequestBody LetterCardRequest request
    ) {
        return ApiResponse.created("카드를 추가했습니다.", letterWriteService.addCard(memberId, letterId, letterCardContentResolver.resolve(memberId, request)));
    }

    @PutMapping("/{letterId}/cards/{cardId}")
    @Operation(summary = "카드 수정", description = """
            카드 내용을 통째로 바꿉니다 (LW-020). 사진을 유지하려면 기존 imageKey를 다시 보내고, 빼려면 비워서 보냅니다.
            - 편지에 없는 카드면 LETTER_005, 전송된 편지면 LETTER_003입니다.
            """)
    public ApiResponse<LetterResponse> updateCard(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId,
            @Parameter(description = "편지 ID", example = "1") @PathVariable Long letterId,
            @Parameter(description = "카드 ID", example = "10") @PathVariable Long cardId,
            @Valid @RequestBody LetterCardRequest request
    ) {
        return ApiResponse.ok("카드를 수정했습니다.", letterWriteService.updateCard(memberId, letterId, cardId,
                letterCardContentResolver.resolve(memberId, request)));
    }

    @DeleteMapping("/{letterId}/cards/{cardId}")
    @Operation(summary = "카드 삭제", description = """
            카드를 삭제하고 남은 카드의 순서를 1부터 다시 매깁니다 (LW-019).
            - 완료된 편지의 마지막 카드를 지우면 편지는 다시 DRAFT(작성 중)가 됩니다.
            """)
    public ApiResponse<LetterResponse> removeCard(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId,
            @Parameter(description = "편지 ID", example = "1") @PathVariable Long letterId,
            @Parameter(description = "카드 ID", example = "10") @PathVariable Long cardId
    ) {
        return ApiResponse.ok("카드를 삭제했습니다.", letterWriteService.removeCard(memberId, letterId, cardId));
    }

    @PutMapping("/{letterId}/doodle-pick")
    @Operation(summary = "두들픽 저장", description = """
            선물 후보 2~3개(각 30자 이내)와 선정 이유(150자 이내)를 저장합니다 (LW-610~621). 편지당 1개이며, 다시 저장하면 통째로 바뀝니다.
            """)
    public ApiResponse<LetterResponse> replaceDoodlePick(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId,
            @Parameter(description = "편지 ID", example = "1") @PathVariable Long letterId,
            @Valid @RequestBody DoodlePickRequest request
    ) {
        return ApiResponse.ok("두들픽을 저장했습니다.", letterWriteService.replaceDoodlePick(memberId, letterId, request));
    }

    @DeleteMapping("/{letterId}/doodle-pick")
    @Operation(summary = "두들픽 삭제", description = "편지에서 두들픽을 뺍니다. 두들픽은 선택 항목입니다.")
    public ApiResponse<LetterResponse> removeDoodlePick(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId,
            @Parameter(description = "편지 ID", example = "1") @PathVariable Long letterId
    ) {
        return ApiResponse.ok("두들픽을 삭제했습니다.", letterWriteService.removeDoodlePick(memberId, letterId));
    }

    @PostMapping("/{letterId}/complete")
    @Operation(summary = "편지 완료", description = """
            작성을 마치고 편지를 COMPLETED(완료) 상태로 만듭니다 (LW-710). 카드가 1장 이상 있어야 합니다(LETTER_004, LW-711).
            - 완료 후에도 보내기 전까지는 수정할 수 있습니다. 보내기는 별도 API로 합니다.
            """)
    public ApiResponse<LetterResponse> complete(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId,
            @Parameter(description = "편지 ID", example = "1") @PathVariable Long letterId
    ) {
        return ApiResponse.ok("편지를 완료했습니다.", letterWriteService.complete(memberId, letterId));
    }
}
