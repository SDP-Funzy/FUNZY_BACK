package com.sdp1617.backend.archive.dto;

import java.util.List;

public record ArchiveHomeResponse(
        Long memberId,
        String nickname,
        String title,
        String profileImageUrl,
        boolean empty,
        List<ArchiveCategorySectionResponse> sections
) {
}
