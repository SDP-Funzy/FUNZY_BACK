package com.sdp1617.backend.global.config;

/**
 * Swagger 태그 이름. "도메인 - 세부" 형식으로 통일하고, 순서와 설명은 {@link OpenApiConfig}에서 한 곳에 정한다.
 * 컨트롤러는 {@code @Tag(name = ApiTags.XXX)}로 이름만 쓴다.
 */
public final class ApiTags {

    public static final String AUTH = "인증 - 회원가입·로그인";
    public static final String AUTH_SOCIAL = "인증 - 소셜 로그인";
    public static final String AUTH_TOKEN = "인증 - 토큰";
    public static final String FRIEND_SEARCH = "친구 - 회원 검색";
    public static final String FRIEND = "친구 - 요청·목록";
    public static final String LETTER_WRITE = "편지 - ① 쓰기";
    public static final String LETTER_INBOX = "편지 - ② 보내기·편지함";
    public static final String LETTER_KOK = "편지 - ③ 마음카드 콕";
    public static final String LETTER_CARD_BOX = "편지 - ④ 마음카드 보관함";
    public static final String ARCHIVE = "아카이브 - 보드";
    public static final String ARCHIVE_COMMENT = "아카이브 - 댓글";
    public static final String MYPAGE_PROFILE = "마이페이지 - 프로필";
    public static final String MYPAGE_ACCOUNT = "마이페이지 - 계정 설정";
    public static final String MYPAGE_NOTIFICATION = "마이페이지 - 알림";
    public static final String MYPAGE_SUPPORT = "마이페이지 - 고객 지원";

    private ApiTags() {
    }
}
