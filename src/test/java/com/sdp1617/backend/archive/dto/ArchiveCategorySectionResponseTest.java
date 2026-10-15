package com.sdp1617.backend.archive.dto;

import com.sdp1617.backend.archive.entity.ArchiveCategory;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** 아카이브 카테고리 보드 대표 사진 (LB-012, #143). */
class ArchiveCategorySectionResponseTest {

    private static ArchiveCardResponse card(long id, String imageUrl) {
        return new ArchiveCardResponse(id, id, ArchiveCategory.MUSIC, imageUrl, "메시지", LocalDateTime.now());
    }

    @Test
    void 대표_사진은_처음_저장된_카드의_사진이다() {
        // 최근에 저장한 순: 3 → 2 → 1
        ArchiveCategorySectionResponse section = ArchiveCategorySectionResponse.of(ArchiveCategory.MUSIC,
                List.of(card(3, "https://img/3.jpg"), card(2, "https://img/2.jpg"), card(1, "https://img/1.jpg")));

        assertEquals("https://img/1.jpg", section.thumbnailImageUrl());
    }

    @Test
    void 처음_저장된_카드에_사진이_없으면_사진_있는_카드_중_가장_먼저_저장된_것을_쓴다() {
        ArchiveCategorySectionResponse section = ArchiveCategorySectionResponse.of(ArchiveCategory.MUSIC,
                List.of(card(3, "https://img/3.jpg"), card(2, "https://img/2.jpg"), card(1, null)));

        assertEquals("https://img/2.jpg", section.thumbnailImageUrl());
    }

    @Test
    void 사진_있는_카드가_없거나_카드가_없으면_null이다() {
        assertNull(ArchiveCategorySectionResponse.of(ArchiveCategory.MUSIC, List.of(card(1, null))).thumbnailImageUrl());
        assertNull(ArchiveCategorySectionResponse.of(ArchiveCategory.MUSIC, List.of()).thumbnailImageUrl());
    }
}
