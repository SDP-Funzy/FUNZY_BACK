package com.sdp1617.backend.global.s3;

import com.sdp1617.backend.global.config.properties.S3Properties;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.S3Error;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.paginators.ListObjectsV2Iterable;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 안 쓰는 사진 정리(#122)에 쓰는 S3 목록 조회·일괄 삭제. */
class S3ImageServiceCleanupTest {

    private static final Instant CUTOFF = Instant.parse("2026-10-08T00:00:00Z");

    private final S3Client s3Client = mock(S3Client.class);
    private final S3ImageService service = new S3ImageService(s3Client, mock(S3Presigner.class),
            new S3Properties(null, new S3Properties.Region("ap-northeast-2"), new S3Properties.S3("bucket", null, 10), null));

    private static S3Object object(String key, Instant lastModified) {
        return S3Object.builder().key(key).lastModified(lastModified).build();
    }

    @Test
    void 기준_시각보다_먼저_올라간_객체만_페이지별로_넘긴다() {
        ListObjectsV2Request request = ListObjectsV2Request.builder().bucket("bucket").prefix("cards/").build();
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(ListObjectsV2Response.builder()
                .contents(object("cards/1/old.jpg", CUTOFF.minusSeconds(1)),
                        object("cards/1/new.jpg", CUTOFF.plusSeconds(1)),
                        object("cards/1/exact.jpg", CUTOFF))
                .isTruncated(false)
                .build());
        when(s3Client.listObjectsV2Paginator(any(ListObjectsV2Request.class)))
                .thenReturn(new ListObjectsV2Iterable(s3Client, request));

        List<List<String>> pages = new ArrayList<>();
        service.forEachPageOfImagesUploadedBefore("cards/", CUTOFF, pages::add);

        assertEquals(List.of(List.of("cards/1/old.jpg")), pages);
    }

    @Test
    void 일괄_삭제는_실패한_key를_빼고_지운_개수를_돌려준다() {
        when(s3Client.deleteObjects(any(DeleteObjectsRequest.class))).thenReturn(DeleteObjectsResponse.builder()
                .errors(S3Error.builder().key("cards/1/b.jpg").code("AccessDenied").message("denied").build())
                .build());

        int deleted = service.deleteImages(List.of("cards/1/a.jpg", "cards/1/b.jpg"));

        assertEquals(1, deleted);
        verify(s3Client).deleteObjects(org.mockito.ArgumentMatchers.<DeleteObjectsRequest>argThat(request ->
                request.delete().objects().stream().map(ObjectIdentifier::key).toList()
                        .equals(List.of("cards/1/a.jpg", "cards/1/b.jpg"))));
    }

    @Test
    void 지울_것이_없으면_S3를_호출하지_않는다() {
        assertEquals(0, service.deleteImages(List.of()));
        verify(s3Client, never()).deleteObjects(any(DeleteObjectsRequest.class));
    }
}
