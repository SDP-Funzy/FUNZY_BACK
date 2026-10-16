package com.sdp1617.backend.mypage.dto;

import com.sdp1617.backend.global.s3.S3ImageService;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

public record ProfileImagePresignedUrlResponse(
        @Schema(description = "이미지를 PUT으로 올릴 presigned URL")
        String uploadUrl,

        @Schema(description = "업로드 완료 후 서버에 보낼 이미지 키", example = "profiles/1/3f2b9c1e-7a4d-4e8b-9c2a-1d5e6f7a8b9c.jpg")
        String imageKey,

        @Schema(description = "업로드가 끝나면 이미지를 볼 수 있는 URL")
        String imageUrl,

        @Schema(description = "업로드할 때 쓸 HTTP 메서드", example = "PUT")
        String method,

        @Schema(description = "uploadUrl 만료 시각 (UTC)", example = "2026-10-16T05:10:00Z")
        Instant expiresAt
) {
    public static ProfileImagePresignedUrlResponse from(S3ImageService.PresignedUpload upload) {
        return new ProfileImagePresignedUrlResponse(
                upload.uploadUrl(),
                upload.imageKey(),
                upload.imageUrl(),
                upload.method(),
                upload.expiresAt()
        );
    }
}
