package com.sdp1617.backend.auth.dto;

import com.sdp1617.backend.global.error.CustomException;
import com.sdp1617.backend.global.error.ErrorCode;
import java.util.regex.Pattern;

/**
 * 아이디(=닉네임, 공개 아이디) 규칙 (H-121, #144). 인스타 아이디처럼 영문 소문자·숫자·마침표·밑줄만 쓰고,
 * 마침표로 시작·끝나거나 연속될 수 없다. 가입·소셜 가입·닉네임 변경·중복 확인에 같은 규칙을 쓴다.
 * 로그인에는 적용하지 않는다 — 규칙 전에 가입한 회원(한글·대문자 등)도 지금 아이디로 계속 로그인한다.
 * 대문자는 소문자로 바꾸지 않고 거절한다 (앱에서 입력할 때 소문자로 바꿔 보내면 된다).
 */
public final class NicknamePolicy {

    /**
     * 빈 값은 통과시킨다 — 빈 값은 @NotBlank만 걸려 항상 "아이디를 입력해주세요."가 나오게 한다
     * (둘 다 걸리면 어느 메시지가 먼저 나올지 정해져 있지 않다).
     */
    public static final String REGEXP = "^$|^(?!\\.)(?!.*\\.\\.)(?!.*\\.$)[a-z0-9._]{2,20}$";
    public static final String MESSAGE =
            "아이디는 2~20자의 영문 소문자, 숫자, 마침표(.), 밑줄(_)만 쓸 수 있고, 마침표로 시작·끝나거나 연달아 쓸 수 없습니다.";
    public static final String DESCRIPTION =
            "아이디. 2~20자, 영문 소문자·숫자·마침표(.)·밑줄(_)만, 마침표로 시작·끝·연속 불가, 중복 불가";

    public static final String REQUIRED_MESSAGE = "아이디를 입력해주세요.";

    private static final Pattern PATTERN = Pattern.compile(REGEXP);

    private NicknamePolicy() {
    }

    /**
     * 요청 본문이 아닌 곳(중복 확인 쿼리 파라미터)에서 쓰는 검사. 본문 요청과 똑같이 앞뒤 공백을 자른 값으로 검사하고,
     * 자른 값을 돌려준다. 어기면 COMMON_002와 규칙 안내 메시지.
     */
    public static String requireValid(String nickname) {
        String trimmed = nickname == null ? "" : nickname.trim();
        if (trimmed.isEmpty()) {
            throw new CustomException(ErrorCode.COMMON_002, REQUIRED_MESSAGE);
        }
        if (!PATTERN.matcher(trimmed).matches()) {
            throw new CustomException(ErrorCode.COMMON_002, MESSAGE);
        }
        return trimmed;
    }
}
