package com.sdp1617.backend.archive.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/** 댓글·답글 목록 페이지 (오래된 순). */
public record ArchiveCommentPageResponse(
        @Schema(description = "이번 페이지가 비었는지", example = "false")
        boolean empty,
        @Schema(description = "이번 페이지 항목 수", example = "20")
        int count,
        @Schema(description = "댓글 목록 (오래된 순)")
        List<ArchiveCommentResponse> comments,
        @Schema(description = "페이지 번호 (0부터)", example = "0")
        int page,
        @Schema(description = "페이지 크기", example = "20")
        int size,
        @Schema(description = "전체 항목 수", example = "42")
        long totalElements,
        @Schema(description = "전체 페이지 수", example = "3")
        int totalPages,
        @Schema(description = "첫 페이지인지", example = "true")
        boolean first,
        @Schema(description = "마지막 페이지인지", example = "false")
        boolean last
) {
    public static <E> ArchiveCommentPageResponse of(Page<E> page, Function<E, ArchiveCommentResponse> mapper) {
        List<ArchiveCommentResponse> comments = page.getContent().stream().map(mapper).toList();
        return new ArchiveCommentPageResponse(comments.isEmpty(), comments.size(), comments, page.getNumber(),
                page.getSize(), page.getTotalElements(), page.getTotalPages(), page.isFirst(), page.isLast());
    }
}
