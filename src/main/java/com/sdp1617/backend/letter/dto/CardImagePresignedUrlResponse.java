package com.sdp1617.backend.letter.dto;

import com.sdp1617.backend.global.s3.S3ImageService;

import java.time.Instant;

public record CardImagePresignedUrlResponse(
        String uploadUrl,
        String imageKey,
        String imageUrl,
        String method,
        Instant expiresAt
) {
    public static CardImagePresignedUrlResponse from(S3ImageService.PresignedUpload upload) {
        return new CardImagePresignedUrlResponse(
                upload.uploadUrl(),
                upload.imageKey(),
                upload.imageUrl(),
                upload.method(),
                upload.expiresAt()
        );
    }
}
