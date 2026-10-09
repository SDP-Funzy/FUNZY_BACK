package com.sdp1617.backend.global.s3;

import com.sdp1617.backend.global.config.properties.S3Properties;
import com.sdp1617.backend.global.error.CustomException;
import com.sdp1617.backend.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * 마음카드/프로필 등 여러 도메인이 공유하는 S3 이미지 업로드(presigned URL 발급 + 업로드 완료 검증) 로직.
 * 각 도메인은 자신의 키 prefix와 실패 시 사용할 ErrorCode만 넘기면 된다.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class S3ImageService {

    private static final long MAX_IMAGE_SIZE_BYTES = 5 * 1024 * 1024;
    private static final long MAX_IMAGE_PIXELS = 40_000_000L;
    private static final Set<String> ALLOWED_IMAGE_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp"
    );

    /**
     * 이전 이미지 삭제(deleteImageQuietlyAsync)용 fire-and-forget 실행기.
     * 가상 스레드라 풀 크기 튜닝이 필요 없고, 병렬 스트림 등이 공유하는 ForkJoinPool.commonPool()과
     * 자원을 다투지 않는다.
     */
    private static final ExecutorService IMAGE_DELETE_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final S3Properties s3Properties;

    public record PresignedUpload(
            String uploadUrl,
            String imageKey,
            String imageUrl,
            String method,
            Instant expiresAt
    ) {
    }

    public PresignedUpload issuePresignedUpload(String keyPrefix, String contentType, ErrorCode invalidFormatError) {
        String normalizedContentType = normalizeContentType(contentType, invalidFormatError);
        validateContentType(normalizedContentType, invalidFormatError);

        String imageKey = keyPrefix + UUID.randomUUID() + extensionFor(normalizedContentType, invalidFormatError);
        Instant expiresAt = Instant.now().plus(Duration.ofMinutes(s3Properties.presignedUrlExpirationMinutes()));
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(s3Properties.bucket())
                .key(imageKey)
                .contentType(normalizedContentType)
                .build();
        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(s3Properties.presignedUrlExpirationMinutes()))
                .putObjectRequest(putObjectRequest)
                .build();
        PresignedPutObjectRequest presignedRequest = s3Presigner.presignPutObject(presignRequest);

        return new PresignedUpload(
                presignedRequest.url().toString(),
                imageKey,
                buildImageUrl(imageKey),
                "PUT",
                expiresAt
        );
    }

    public void validateOwnership(String imageKey, String keyPrefix) {
        if (!imageKey.startsWith(keyPrefix)) {
            throw new CustomException(ErrorCode.COMMON_004);
        }
    }

    public void validateUploadedImage(
            String imageKey,
            ErrorCode notFoundError,
            ErrorCode invalidFormatError,
            ErrorCode tooLargeError
    ) {
        HeadObjectResponse objectMetadata = getImageObjectMetadata(imageKey, notFoundError);
        String contentType = normalizeContentType(objectMetadata.contentType(), invalidFormatError);
        validateContentType(contentType, invalidFormatError);
        validateImageSize(objectMetadata.contentLength(), invalidFormatError, tooLargeError);
        byte[] imageBytes = readImageObject(imageKey, notFoundError, invalidFormatError);
        validateImageDecodable(imageBytes, contentType, invalidFormatError);
    }

    public String buildImageUrl(String imageKey) {
        if (s3Properties.publicBaseUrl() != null && !s3Properties.publicBaseUrl().isBlank()) {
            return s3Properties.publicBaseUrl().replaceAll("/$", "") + "/" + imageKey;
        }
        return "https://" + s3Properties.bucket() + ".s3." + s3Properties.staticRegion() + ".amazonaws.com/" + imageKey;
    }

    /**
     * 이전 이미지 정리용. 실패해도 주 흐름(이미지 교체/초기화)을 막지 않도록 예외를 삼키고 로그만 남긴다.
     */
    public void deleteImageQuietly(String imageKey) {
        if (imageKey == null || imageKey.isBlank()) {
            return;
        }
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(s3Properties.bucket())
                    .key(imageKey)
                    .build());
        } catch (SdkException exception) {
            log.warn("이전 S3 이미지 삭제 실패: key={}", imageKey, exception);
        }
    }

    /**
     * deleteImageQuietly를 별도 스레드에서 실행한다. 트랜잭션 afterCommit 콜백 등
     * DB 커넥션이 아직 반납되지 않은 시점에서 블로킹 S3 호출로 그 반납을 지연시키지 않기 위함.
     * deleteImageQuietly가 잡지 못하는 예외(SdkException 외)까지 로그로 남긴다.
     */
    public void deleteImageQuietlyAsync(String imageKey) {
        if (imageKey == null || imageKey.isBlank()) {
            return;
        }
        CompletableFuture.runAsync(() -> deleteImageQuietly(imageKey), IMAGE_DELETE_EXECUTOR)
                .exceptionally(exception -> {
                    log.warn("비동기 S3 이미지 삭제 중 예상치 못한 예외: key={}", imageKey, exception);
                    return null;
                });
    }

    /**
     * prefix 아래에서 before보다 먼저 올라간 객체의 key를 S3 목록 한 페이지(최대 1000개)씩 넘긴다 (#122 정리 작업용).
     * 버킷 목록 조회 권한(s3:ListBucket)이 필요하다.
     */
    public void forEachPageOfImagesUploadedBefore(String prefix, Instant before, Consumer<List<String>> pageConsumer) {
        ListObjectsV2Request request = ListObjectsV2Request.builder()
                .bucket(s3Properties.bucket())
                .prefix(prefix)
                .build();
        for (ListObjectsV2Response page : s3Client.listObjectsV2Paginator(request)) {
            List<String> keys = page.contents().stream()
                    .filter(object -> object.lastModified().isBefore(before))
                    .map(S3Object::key)
                    .toList();
            if (!keys.isEmpty()) {
                pageConsumer.accept(keys);
            }
        }
    }

    /** 여러 객체를 한 번에 지운다 (최대 1000개). 지우지 못한 key는 로그로 남기고, 지운 개수를 돌려준다. */
    public int deleteImages(List<String> imageKeys) {
        if (imageKeys.isEmpty()) {
            return 0;
        }
        DeleteObjectsResponse response = s3Client.deleteObjects(DeleteObjectsRequest.builder()
                .bucket(s3Properties.bucket())
                .delete(Delete.builder()
                        .objects(imageKeys.stream().map(key -> ObjectIdentifier.builder().key(key).build()).toList())
                        .quiet(true)
                        .build())
                .build());
        response.errors().forEach(error ->
                log.warn("S3 이미지 삭제 실패: key={}, code={}, message={}", error.key(), error.code(), error.message()));
        return imageKeys.size() - response.errors().size();
    }

    private HeadObjectResponse getImageObjectMetadata(String imageKey, ErrorCode notFoundError) {
        try {
            return s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(s3Properties.bucket())
                    .key(imageKey)
                    .build());
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) {
                throw new CustomException(notFoundError);
            }
            throw exception;
        }
    }

    private void validateImageSize(Long contentLength, ErrorCode invalidFormatError, ErrorCode tooLargeError) {
        if (contentLength == null || contentLength <= 0) {
            throw new CustomException(invalidFormatError);
        }
        if (contentLength > MAX_IMAGE_SIZE_BYTES) {
            throw new CustomException(tooLargeError);
        }
    }

    private byte[] readImageObject(String imageKey, ErrorCode notFoundError, ErrorCode invalidFormatError) {
        try (ResponseInputStream<GetObjectResponse> object = s3Client.getObject(GetObjectRequest.builder()
                .bucket(s3Properties.bucket())
                .key(imageKey)
                .build())) {
            return object.readAllBytes();
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) {
                throw new CustomException(notFoundError);
            }
            throw exception;
        } catch (IOException exception) {
            throw new CustomException(invalidFormatError);
        }
    }

    /**
     * 매직바이트만이 아니라 실제로 디코딩까지 성공해야 유효한 이미지로 인정한다(CWE-434 대응).
     * 애니메이션 WebP 등 다중 프레임 이미지는 정적 이미지 업로드 용도에 맞지 않아 거부한다.
     */
    private void validateImageDecodable(byte[] imageBytes, String contentType, ErrorCode invalidFormatError) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(imageBytes))) {
            if (input == null) {
                throw new CustomException(invalidFormatError);
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new CustomException(invalidFormatError);
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, false, true);
                if (!matchesDeclaredFormat(reader.getFormatName(), contentType)) {
                    throw new CustomException(invalidFormatError);
                }
                if (reader.getNumImages(true) > 1) {
                    throw new CustomException(invalidFormatError);
                }
                long pixelCount = (long) reader.getWidth(0) * reader.getHeight(0);
                if (pixelCount <= 0 || pixelCount > MAX_IMAGE_PIXELS) {
                    // 헤더만으로 해상도를 먼저 확인해 디코딩 폭탄(작은 용량, 초대형 해상도)으로 인한 OOM을 막는다.
                    throw new CustomException(invalidFormatError);
                }
                BufferedImage image = reader.read(0);
                if (image.getWidth() <= 0 || image.getHeight() <= 0) {
                    throw new CustomException(invalidFormatError);
                }
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof CustomException customException) {
                throw customException;
            }
            throw new CustomException(invalidFormatError);
        }
    }

    private boolean matchesDeclaredFormat(String readerFormatName, String contentType) {
        String normalizedFormatName = readerFormatName.toLowerCase(Locale.ROOT);
        return switch (contentType) {
            case "image/jpeg" -> normalizedFormatName.contains("jpeg");
            case "image/png" -> normalizedFormatName.contains("png");
            case "image/webp" -> normalizedFormatName.contains("webp");
            default -> false;
        };
    }

    private void validateContentType(String contentType, ErrorCode invalidFormatError) {
        if (!ALLOWED_IMAGE_CONTENT_TYPES.contains(contentType)) {
            throw new CustomException(invalidFormatError);
        }
    }

    private String extensionFor(String contentType, ErrorCode invalidFormatError) {
        return switch (contentType) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> throw new CustomException(invalidFormatError);
        };
    }

    private String normalizeContentType(String contentType, ErrorCode invalidFormatError) {
        if (contentType == null) {
            throw new CustomException(invalidFormatError);
        }
        return contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
    }
}
