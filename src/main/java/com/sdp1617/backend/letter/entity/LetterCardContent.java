package com.sdp1617.backend.letter.entity;

import com.sdp1617.backend.archive.entity.ArchiveCategory;

/** 카드 1장의 내용. 이미지는 S3 업로드 검증을 마친 key와 URL. */
public record LetterCardContent(
        ArchiveCategory category,
        String customCategory,
        String content,
        String link,
        String linkTitle,
        String imageKey,
        String imageUrl
) {
}
