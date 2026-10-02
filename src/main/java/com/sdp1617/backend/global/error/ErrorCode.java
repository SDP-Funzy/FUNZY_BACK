package com.sdp1617.backend.global.error;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    // 에러 코드 네이밍 가이드: DOMAIN_nnn (예: AUTH_001, LETTER_002)

    COMMON_001(HttpStatus.NOT_FOUND, "COMMON_001", "요청하신 리소스를 찾을 수 없습니다."),
    COMMON_002(HttpStatus.BAD_REQUEST, "COMMON_002", "요청 값이 올바르지 않습니다."),
    COMMON_003(HttpStatus.UNAUTHORIZED, "COMMON_003", "인증이 필요합니다."),
    COMMON_004(HttpStatus.FORBIDDEN, "COMMON_004", "접근 권한이 없습니다."),
    COMMON_005(HttpStatus.CONFLICT, "COMMON_005", "데이터 제약 조건을 위반했습니다."),
    COMMON_999(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON_999", "서버 내부 오류가 발생했습니다."),

    AUTH_001(HttpStatus.UNAUTHORIZED, "AUTH_001", "아이디 또는 비밀번호가 일치하지 않습니다."),
    AUTH_002(HttpStatus.NOT_FOUND, "AUTH_002", "존재하지 않는 회원입니다."),
    AUTH_003(HttpStatus.UNAUTHORIZED, "AUTH_003", "유효하지 않은 토큰입니다."),
    AUTH_004(HttpStatus.UNAUTHORIZED, "AUTH_004", "만료된 토큰입니다."),
    AUTH_005(HttpStatus.UNAUTHORIZED, "AUTH_005", "저장된 refresh token을 찾을 수 없습니다."),
    AUTH_006(HttpStatus.CONFLICT, "AUTH_006", "이미 가입된 이메일입니다."),
    AUTH_007(HttpStatus.CONFLICT, "AUTH_007", "이미 사용 중인 닉네임입니다."),
    AUTH_008(HttpStatus.BAD_REQUEST, "AUTH_008", "비밀번호가 일치하지 않습니다."),
    AUTH_010(HttpStatus.LOCKED, "AUTH_010", "5회 로그인 실패로 계정이 잠겼습니다. 이메일 인증으로 잠금을 해제해주세요."),
    AUTH_011(HttpStatus.BAD_REQUEST, "AUTH_011", "유효하지 않거나 만료된 링크입니다."),
    AUTH_012(HttpStatus.CONFLICT, "AUTH_012", "이미 다른 방식으로 가입된 이메일입니다."),
    AUTH_013(HttpStatus.UNAUTHORIZED, "AUTH_013", "소셜 인증에 실패했습니다."),
    AUTH_014(HttpStatus.BAD_REQUEST, "AUTH_014", "소셜 전용 계정은 비밀번호를 변경할 수 없습니다."),
    AUTH_015(HttpStatus.BAD_REQUEST, "AUTH_015", "현재 비밀번호가 일치하지 않습니다."),
    AUTH_016(HttpStatus.FORBIDDEN, "AUTH_016", "이메일 인증이 필요합니다. 인증 메일을 확인해주세요."),
    AUTH_017(HttpStatus.CONFLICT, "AUTH_017", "이미 다른 계정에 연결된 소셜 계정입니다."),
    AUTH_018(HttpStatus.CONFLICT, "AUTH_018", "이미 연결된 소셜 계정입니다."),
    AUTH_019(HttpStatus.NOT_FOUND, "AUTH_019", "연결되지 않은 소셜 계정입니다."),
    AUTH_020(HttpStatus.BAD_REQUEST, "AUTH_020", "마지막 남은 로그인 수단은 연결 해제할 수 없습니다."),
    AUTH_021(HttpStatus.BAD_REQUEST, "AUTH_021", "인증번호가 일치하지 않습니다."),
    AUTH_022(HttpStatus.BAD_REQUEST, "AUTH_022", "인증번호가 만료되었습니다. 인증번호를 다시 받아주세요."),
    AUTH_023(HttpStatus.BAD_REQUEST, "AUTH_023", "인증번호 입력 횟수를 초과했습니다. 인증번호를 다시 받아주세요."),
    AUTH_024(HttpStatus.TOO_MANY_REQUESTS, "AUTH_024", "인증번호는 1분 후에 다시 요청할 수 있습니다."),
    AUTH_025(HttpStatus.TOO_MANY_REQUESTS, "AUTH_025", "인증번호 요청 횟수를 초과했습니다. 잠시 후 다시 시도해주세요."),
    AUTH_026(HttpStatus.BAD_REQUEST, "AUTH_026", "이메일 인증이 만료되었거나 유효하지 않습니다. 이메일 인증을 다시 진행해주세요."),

    ARCHIVE_001(HttpStatus.CONFLICT, "ARCHIVE_001", "이미 저장된 카드입니다."),
    ARCHIVE_002(HttpStatus.NOT_FOUND, "ARCHIVE_002", "존재하지 않는 아카이브 카드입니다."),
    ARCHIVE_003(HttpStatus.BAD_REQUEST, "ARCHIVE_003", "내 카드에는 좋아요를 누를 수 없습니다."),

    CARD_001(HttpStatus.NOT_FOUND, "CARD_001", "존재하지 않는 마음카드입니다."),
    CARD_002(HttpStatus.BAD_REQUEST, "CARD_002", "이미지 업로드가 완료되지 않았습니다."),
    CARD_003(HttpStatus.BAD_REQUEST, "CARD_003", "지원하지 않는 이미지 형식입니다."),
    CARD_004(HttpStatus.BAD_REQUEST, "CARD_004", "이미지 파일 크기가 허용 범위를 초과했습니다."),

    SOCIAL_001(HttpStatus.NOT_FOUND, "SOCIAL_001", "존재하지 않는 친구 코드입니다."),
    SOCIAL_002(HttpStatus.BAD_REQUEST, "SOCIAL_002", "본인에게는 팔로우 요청을 보낼 수 없습니다."),
    SOCIAL_003(HttpStatus.CONFLICT, "SOCIAL_003", "이미 친구인 회원입니다."),
    SOCIAL_004(HttpStatus.CONFLICT, "SOCIAL_004", "이미 보낸 팔로우 요청이 있습니다."),
    SOCIAL_005(HttpStatus.BAD_REQUEST, "SOCIAL_005", "친구 수 상한을 초과했습니다."),
    SOCIAL_006(HttpStatus.NOT_FOUND, "SOCIAL_006", "존재하지 않는 팔로우 요청입니다."),
    SOCIAL_007(HttpStatus.NOT_FOUND, "SOCIAL_007", "친구 관계가 아닙니다."),

    NOTIFICATION_001(HttpStatus.NOT_FOUND, "NOTIFICATION_001", "존재하지 않는 알림입니다."),

    MYPAGE_001(HttpStatus.BAD_REQUEST, "MYPAGE_001", "이미지 업로드가 완료되지 않았습니다."),
    MYPAGE_002(HttpStatus.BAD_REQUEST, "MYPAGE_002", "지원하지 않는 이미지 형식입니다."),
    MYPAGE_003(HttpStatus.BAD_REQUEST, "MYPAGE_003", "이미지 파일 크기가 허용 범위를 초과했습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    ErrorCode(HttpStatus httpStatus, String code, String message) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.message = message;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
