package com.sdp1617.backend.letter.dto;

import com.sdp1617.backend.archive.entity.ArchiveCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 카드 1장 작성·수정 요청. 수정은 전체 교체(이미지도 다시 보내야 유지). 카드에는 제목이 없다 (피그마 기준, #113). */
public record LetterCardRequest(
        @Schema(description = "카테고리 (LW-210)", example = "MUSIC")
        @NotNull(message = "카테고리는 필수입니다.")
        ArchiveCategory category,

        @Schema(description = "카테고리가 ETC일 때 직접 입력한 이름 (LW-211, 10자 이내). ETC가 아니면 무시", example = "게임")
        @Size(max = 10, message = "직접 입력 카테고리는 10자 이내여야 합니다.")
        String customCategory,

        @Schema(description = "본문 (500자 이내)", example = "출근길마다 듣는데 네 생각이 났어")
        @NotBlank(message = "본문은 필수입니다.")
        @Size(max = 500, message = "본문은 500자 이내여야 합니다.")
        String content,

        @Schema(description = """
                첨부 링크 (선택). http:// 또는 https://로 시작해야 하고 공백·제어 문자는 넣을 수 없음 (어기면 COMMON_002).
                사용자가 youtu.be/xyz처럼 http(s) 없이 입력하면 앱에서 https://를 붙여서 보내 주세요.""",
                example = "https://music.example.com/track/1")
        @Size(max = 1000, message = "링크는 1000자 이내여야 합니다.")
        @Pattern(regexp = LINK_REGEXP, message = "링크는 http:// 또는 https://로 시작하는 주소만 넣을 수 있습니다.")
        String link,

        @Schema(description = "링크 제목 (선택)", example = "좋아하는 노래")
        @Size(max = 100, message = "링크 제목은 100자 이내여야 합니다.")
        String linkTitle,

        @Schema(description = "사진 (선택). 마음카드 이미지 presigned URL로 업로드를 마친 imageKey", example = "cards/1/abc.jpg")
        String imageKey
) {
    /** javascript:·data: 등은 받는 사람 브라우저에서 스크립트로 실행될 수 있어 http(s)만 허용 (#155). 브라우저는 scheme 사이의 탭·줄바꿈을 지우므로 공백·제어 문자도 막는다. (?U)로 전각 공백 등 유니코드 공백까지 포함. */
    static final String LINK_REGEXP = "(?iU)https?://[^\\s\\p{Cntrl}]+";

    public LetterCardRequest {
        customCategory = blankToNull(customCategory);
        link = blankToNull(link);
        linkTitle = blankToNull(linkTitle);
        imageKey = blankToNull(imageKey);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
