package com.sdp1617.backend.global.error;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 이 API가 돌려줄 수 있는 에러 코드. Swagger 응답 칸에 HTTP 상태별로 묶어 예시를 자동으로 만든다
 * ({@link com.sdp1617.backend.global.config.ApiErrorCodesCustomizer}). 예시는 {@link ErrorCode}에서 만들므로 메시지·상태가 어긋나지 않는다.
 * 로그인이 필요한 API 공통 인증 오류(AUTH_003·AUTH_004·AUTH_027·COMMON_003)는 문서 맨 위에 한 번만 적고 여기에는 넣지 않는다.
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ApiErrorCodes {

    ErrorCode[] value();
}
