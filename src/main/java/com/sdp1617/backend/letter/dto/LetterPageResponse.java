package com.sdp1617.backend.letter.dto;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/** 편지함 목록 페이지 (받은 편지함 응답과 같은 형식). */
public record LetterPageResponse<T>(
        boolean empty,
        int count,
        List<T> letters,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {
    public static <E, T> LetterPageResponse<T> of(Page<E> page, Function<E, T> mapper) {
        List<T> letters = page.getContent().stream().map(mapper).toList();
        return new LetterPageResponse<>(letters.isEmpty(), letters.size(), letters, page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.isFirst(), page.isLast());
    }
}
