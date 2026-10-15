package com.sdp1617.backend.archive.dto;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/** 댓글·답글 목록 페이지 (오래된 순). */
public record ArchiveCommentPageResponse(
        boolean empty,
        int count,
        List<ArchiveCommentResponse> comments,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {
    public static <E> ArchiveCommentPageResponse of(Page<E> page, Function<E, ArchiveCommentResponse> mapper) {
        List<ArchiveCommentResponse> comments = page.getContent().stream().map(mapper).toList();
        return new ArchiveCommentPageResponse(comments.isEmpty(), comments.size(), comments, page.getNumber(),
                page.getSize(), page.getTotalElements(), page.getTotalPages(), page.isFirst(), page.isLast());
    }
}
