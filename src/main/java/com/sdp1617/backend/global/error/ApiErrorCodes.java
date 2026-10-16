package com.sdp1617.backend.global.error;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 이 API가 돌려줄 수 있는 에러 코드. Swagger 응답 칸에 HTTP 상태별로 묶어 예시를 자동으로 만든다
 * ({@link com.sdp1617.backend.global.config.ApiErrorCodesCustomizer}). 예시는 {@link ErrorCode}에서 만들므로 코드·상태가 어긋나지 않는다
 * (COMMON_002의 message만 잘못된 값에 따라 구체적인 안내로 바뀐다).
 * 모든 API 공통 오류는 문서 맨 위에 한 번만 적고 여기에는 넣지 않는다: Authorization 헤더의 access token 오류
 * (AUTH_003·AUTH_004·AUTH_027·COMMON_003)와 경로 값 형식 오류(COMMON_002). 요청 본문으로 받은 토큰의 오류
 * (예: 토큰 재발급·로그아웃의 refresh token)는 그 API의 오류라 여기에 넣는다.
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ApiErrorCodes {

    ErrorCode[] value();
}
