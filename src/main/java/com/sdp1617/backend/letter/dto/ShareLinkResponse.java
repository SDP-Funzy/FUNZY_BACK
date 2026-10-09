package com.sdp1617.backend.letter.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/** 발급한 공유 링크 (#87). 앱은 이 토큰으로 공유할 주소를 만든다. */
public record ShareLinkResponse(
        @Schema(description = "공유 링크 토큰. 열람은 GET /api/letters/shared/{token}", example = "q3J8yQ0m1i2Gx7v3cUoK6fQe5tWZp4sR9nBhLdYaE0c")
        String token,
        @Schema(description = "링크 만료 시각 (발급 후 30일)")
        LocalDateTime expiresAt
) {
}
