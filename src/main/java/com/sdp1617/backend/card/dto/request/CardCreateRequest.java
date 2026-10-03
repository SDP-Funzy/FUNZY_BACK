package com.sdp1617.backend.card.dto.request;

import com.sdp1617.backend.archive.entity.ArchiveCategory;
import com.sdp1617.backend.card.dto.DesignType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** 보내는 사람은 요청 바디가 아니라 로그인 사용자로 정한다 (바디로 받으면 다른 회원 이름으로 보낼 수 있음). */
public record CardCreateRequest(
        Long receiverId,
        DesignType designType,
        @NotBlank String title,
        @NotNull ArchiveCategory category,
        String link,
        String linkTitle,
        @NotBlank String content,
        String imageKey
) {
}
