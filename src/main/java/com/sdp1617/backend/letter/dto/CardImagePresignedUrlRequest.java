package com.sdp1617.backend.letter.dto;

import jakarta.validation.constraints.NotBlank;

public record CardImagePresignedUrlRequest(
        @NotBlank String fileName,
        @NotBlank String contentType
) {
}
