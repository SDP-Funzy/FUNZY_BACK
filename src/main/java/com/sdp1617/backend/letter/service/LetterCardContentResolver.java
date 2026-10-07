package com.sdp1617.backend.letter.service;

import com.sdp1617.backend.global.error.ErrorCode;
import com.sdp1617.backend.global.s3.S3ImageService;
import com.sdp1617.backend.letter.dto.LetterCardRequest;
import com.sdp1617.backend.letter.entity.LetterCardContent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 카드 요청을 저장할 내용으로 바꾼다. 사진이 있으면 S3에 업로드가 끝났는지(HEAD/GET) 확인하므로,
 * DB 트랜잭션을 열기 전에 호출한다 — 외부 호출 동안 DB 커넥션을 붙잡지 않기 위해서다.
 */
@Component
@RequiredArgsConstructor
public class LetterCardContentResolver {

    private final S3ImageService s3ImageService;

    /** 사진은 본인에게 발급된 마음카드 이미지 경로(cards/{memberId}/)로 업로드를 마친 것만 쓸 수 있다. */
    public LetterCardContent resolve(Long memberId, LetterCardRequest request) {
        String imageUrl = null;
        if (request.imageKey() != null) {
            s3ImageService.validateOwnership(request.imageKey(), "cards/" + memberId + "/");
            s3ImageService.validateUploadedImage(
                    request.imageKey(), ErrorCode.CARD_002, ErrorCode.CARD_003, ErrorCode.CARD_004);
            imageUrl = s3ImageService.buildImageUrl(request.imageKey());
        }
        return new LetterCardContent(request.category(), request.customCategory(), request.title(), request.content(),
                request.link(), request.linkTitle(), request.imageKey(), imageUrl);
    }
}
