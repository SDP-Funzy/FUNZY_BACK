package com.sdp1617.backend.global.common.response;

/**
 * 공통 응답 형식. 성공 응답은 생성 API를 포함해 모두 HTTP 200, code "200"이다 (#116).
 * 실패 응답은 GlobalExceptionHandler가 에러 코드의 HTTP 상태로 내려준다.
 */
public record ApiResponse<T>(
        boolean success,
        String code,
        String message,
        T data
) {
    public static <T> ApiResponse<T> ok(String message, T data) {
        return new ApiResponse<>(true, "200", message, data);
    }
}
