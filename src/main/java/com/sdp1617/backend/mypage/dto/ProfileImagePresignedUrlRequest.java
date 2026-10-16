package com.sdp1617.backend.mypage.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record ProfileImagePresignedUrlRequest(
        @Schema(description = "업로드할 이미지 형식: image/jpeg, image/png, image/webp", example = "image/jpeg")
        @NotBlank String contentType
) {
}
