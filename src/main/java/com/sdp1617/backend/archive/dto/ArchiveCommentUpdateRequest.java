package com.sdp1617.backend.archive.dto;

import com.sdp1617.backend.archive.entity.ArchiveCardComment;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "아카이브 카드 댓글 수정 요청")
public record ArchiveCommentUpdateRequest(
        @Schema(description = "바꿀 내용 (1~200자, 공백만은 불가)", example = "이 노래 나도 진짜 좋아해!",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = ArchiveCardComment.MAX_CONTENT_LENGTH) String content
) {
    /** 앞뒤 공백을 지운 뒤 검증한다 (1~200자). */
    public ArchiveCommentUpdateRequest {
        content = content == null ? null : content.strip();
    }
}
