package com.sdp1617.backend.archive.dto;

import com.sdp1617.backend.archive.entity.ArchiveCardComment;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "아카이브 카드 댓글·답글 작성 요청")
public record ArchiveCommentCreateRequest(
        @Schema(description = "댓글 내용 (1~200자, 공백만은 불가)", example = "이 노래 나도 좋아해!",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = ArchiveCardComment.MAX_CONTENT_LENGTH) String content,

        @Schema(description = """
                답글이면 답할 댓글 ID, 원댓글이면 생략. 답글에 답하면 같은 원댓글 아래에 달립니다(인스타처럼 한 단계).
                누구에게 답하는지는 본문에 @아이디로 넣어 주세요.""", example = "3")
        Long parentCommentId
) {
    /** 앞뒤 공백을 지운 뒤 검증한다 (1~200자). */
    public ArchiveCommentCreateRequest {
        content = content == null ? null : content.strip();
    }
}
