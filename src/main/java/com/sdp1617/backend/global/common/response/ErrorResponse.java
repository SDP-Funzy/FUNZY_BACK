package com.sdp1617.backend.global.common.response;

import com.sdp1617.backend.global.error.ErrorCode;
import io.swagger.v3.oas.annotations.media.Schema;

/** 실패 응답 본문. 형식은 성공 응답({@link ApiResponse})과 같다. */
@Schema(name = "ErrorResponse", description = "실패 응답")
public record ErrorResponse(
        @Schema(description = "실패면 항상 false", example = "false")
        boolean success,

        @Schema(description = "에러 코드. 앱은 이 값으로 분기합니다", example = "LETTER_001")
        String code,

        @Schema(description = "화면에 보여줄 수 있는 안내 메시지", example = "존재하지 않는 편지입니다.")
        String message,

        @Schema(description = "실패 응답은 null")
        Object data
) {
    public static ErrorResponse of(ErrorCode errorCode) {
        return new ErrorResponse(false, errorCode.getCode(), errorCode.getMessage(), null);
    }

    public static ErrorResponse of(ErrorCode errorCode, String message) {
        return new ErrorResponse(false, errorCode.getCode(), message, null);
    }

    public static ErrorResponse of(ErrorCode errorCode, String message, Object data) {
        return new ErrorResponse(false, errorCode.getCode(), message, data);
    }
}
