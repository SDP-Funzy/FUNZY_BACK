package com.sdp1617.backend.archive.controller;

import com.sdp1617.backend.archive.dto.ArchiveCommentCreateRequest;
import com.sdp1617.backend.archive.dto.ArchiveCommentPageResponse;
import com.sdp1617.backend.archive.dto.ArchiveCommentResponse;
import com.sdp1617.backend.archive.dto.ArchiveCommentUpdateRequest;
import com.sdp1617.backend.archive.service.ArchiveCommentService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "아카이브 댓글", description = """
        아카이브 카드에 다는 인스타식 댓글 API (#164). 카드 주인과 주인의 친구가 쓰고 볼 수 있습니다.
        - 비공개(잠금) 카드는 주인만 볼 수 있습니다. 볼 수 없는 카드면 ARCHIVE_002, 볼 수 없는 댓글이면 ARCHIVE_004입니다.
        - 답글은 한 단계만: 답글에 답하면 같은 원댓글 아래에 달립니다. 원댓글을 지우면 답글도 지워집니다.
        - 남이 내 카드에 댓글·답글을 달면 카드 주인에게, 내 댓글에 답글이 달리면 그 댓글 작성자에게 COMMENT 알림이 갑니다
          (답글에 단 답글도 실제로 답한 사람에게. 카드를 볼 수 없게 된 사람에게는 안 갑니다).
        - 탈퇴한 회원의 댓글은 남고 작성자는 "탈퇴한회원N"으로 보입니다. 친구를 끊으면 더 보거나 쓸 수 없지만 내 댓글은 지울 수 있습니다.
        """)
public class ArchiveCommentController {

    private final ArchiveCommentService archiveCommentService;

    @GetMapping("/api/archive/cards/{archiveCardId}/comments")
    @Operation(summary = "댓글 목록", description = "원댓글을 오래된 순으로 조회합니다. replyCount가 있으면 답글 목록 API로 답글을 불러옵니다.")
    public ApiResponse<ArchiveCommentPageResponse> getComments(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId,
            @Parameter(description = "아카이브 카드 ID", example = "1") @PathVariable Long archiveCardId,
            @Parameter(description = "페이지 번호 (0부터 시작)", example = "0") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기 (기본 20, 최대 100)", example = "20") @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.ok("댓글 목록을 조회했습니다.",
                archiveCommentService.getComments(memberId, archiveCardId, page, size));
    }

    @GetMapping("/api/archive/cards/{archiveCardId}/comments/{commentId}/replies")
    @Operation(summary = "답글 목록", description = "원댓글의 답글을 오래된 순으로 조회합니다. 답글 ID를 넣으면 ARCHIVE_004입니다.")
    public ApiResponse<ArchiveCommentPageResponse> getReplies(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId,
            @Parameter(description = "아카이브 카드 ID", example = "1") @PathVariable Long archiveCardId,
            @Parameter(description = "원댓글 ID", example = "3") @PathVariable Long commentId,
            @Parameter(description = "페이지 번호 (0부터 시작)", example = "0") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기 (기본 20, 최대 100)", example = "20") @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.ok("답글 목록을 조회했습니다.",
                archiveCommentService.getReplies(memberId, archiveCardId, commentId, page, size));
    }

    @PostMapping("/api/archive/cards/{archiveCardId}/comments")
    @Operation(summary = "댓글·답글 작성", description = """
            댓글을 남깁니다. parentCommentId를 넣으면 답글입니다 (다른 카드의 댓글이면 ARCHIVE_004).
            내용은 1~200자이고 앞뒤 공백은 지웁니다. 공백만이거나 200자를 넘으면 COMMON_002입니다.
            """)
    public ApiResponse<ArchiveCommentResponse> createComment(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId,
            @Parameter(description = "아카이브 카드 ID", example = "1") @PathVariable Long archiveCardId,
            @Valid @RequestBody ArchiveCommentCreateRequest request
    ) {
        return ApiResponse.ok("댓글을 남겼습니다.", archiveCommentService.createComment(memberId, archiveCardId, request));
    }

    @PatchMapping("/api/archive/comments/{commentId}")
    @Operation(summary = "댓글 수정", description = "내가 쓴 댓글만 수정할 수 있습니다 (남의 댓글이면 COMMON_004). 카드를 더 볼 수 없으면 ARCHIVE_004입니다.")
    public ApiResponse<ArchiveCommentResponse> updateComment(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId,
            @Parameter(description = "댓글 ID", example = "3") @PathVariable Long commentId,
            @Valid @RequestBody ArchiveCommentUpdateRequest request
    ) {
        return ApiResponse.ok("댓글을 수정했습니다.", archiveCommentService.updateComment(memberId, commentId, request));
    }

    @DeleteMapping("/api/archive/comments/{commentId}")
    @Operation(summary = "댓글 삭제", description = """
            내가 쓴 댓글, 또는 내 카드에 달린 댓글을 지웁니다. 원댓글을 지우면 답글도 함께 지워집니다.
            내가 쓴 댓글은 친구를 끊었거나 카드가 비공개가 된 뒤에도 지울 수 있습니다.
            남의 댓글이면: 지울 권한이 없으면 COMMON_004, 볼 수 없는 댓글이면 ARCHIVE_004입니다.
            """)
    public ApiResponse<Void> deleteComment(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId,
            @Parameter(description = "댓글 ID", example = "3") @PathVariable Long commentId
    ) {
        archiveCommentService.deleteComment(memberId, commentId);
        return ApiResponse.ok("댓글을 삭제했습니다.", null);
    }
}
