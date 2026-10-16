package com.sdp1617.backend.mypage.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record ProfileImageUploadCompleteRequest(
        @Schema(description = "presigned URL 발급 응답의 imageKey", example = "profiles/1/3f2b9c1e-7a4d-4e8b-9c2a-1d5e6f7a8b9c.jpg")
        @NotBlank String imageKey
) {
}
