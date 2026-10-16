package com.sdp1617.backend.global.common.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 공통 응답 형식. 성공 응답은 생성 API를 포함해 모두 HTTP 200, code "200"이다 (#116).
 * 실패 응답은 GlobalExceptionHandler가 에러 코드의 HTTP 상태로 내려준다.
 */
public record ApiResponse<T>(
        @Schema(description = "성공 여부. 성공이면 true", example = "true")
        boolean success,

        @Schema(description = "성공이면 \"200\", 실패면 에러 코드 (예: \"LETTER_001\")", example = "200")
        String code,

        @Schema(description = "화면에 보여줄 수 있는 안내 메시지")
        String message,

        @Schema(description = "응답 데이터. 데이터가 없는 API와 실패 응답은 null")
        T data
) {
    public static <T> ApiResponse<T> ok(String message, T data) {
        return new ApiResponse<>(true, "200", message, data);
    }
}
