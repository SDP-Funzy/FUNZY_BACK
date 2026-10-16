package com.sdp1617.backend.letter.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record CardImagePresignedUrlRequest(
        @Schema(description = "원본 파일 이름 (서버에서 쓰지 않음, 보내지 않아도 됨)", example = "photo.jpg")
        String fileName,

        @Schema(description = "업로드할 이미지 형식: image/jpeg, image/png, image/webp", example = "image/jpeg")
        @NotBlank String contentType
) {
}
