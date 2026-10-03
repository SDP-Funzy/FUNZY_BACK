package com.sdp1617.backend.auth.util;

import java.util.Locale;

/**
 * 이메일 비교 기준을 한 곳에서 정한다: 앞뒤 공백 제거 + 소문자.
 * 저장(이메일 가입·소셜 가입)과 조회(중복 확인·아이디 찾기·비밀번호 재설정 등)가 같은 규칙을 써야
 * 대소문자만 다른 같은 이메일로 계정이 두 개 생기거나, 가입한 계정을 못 찾는 일이 없다.
 * (DB 유니크 제약 uk_member_email은 대소문자를 구분하므로 애플리케이션에서 맞춘다)
 */
public final class Emails {

    private Emails() {
    }

    /**
     * null 또는 공백뿐인 값은 null로 돌려준다 (소셜 provider가 빈 문자열을 주는 경우 포함).
     * 공백 판단과 제거를 한 기준({@link #isSpace})으로 한다 — isBlank()/trim()/strip()은 공백으로 보는 문자가
     * 서로 달라 섞어 쓰면 일부 공백이 남는다. 복사·붙여넣기로 자주 섞이는 줄바꿈 없는 공백(NBSP 등)도 포함한다.
     */
    public static String normalize(String email) {
        if (email == null) {
            return null;
        }
        int start = 0;
        int end = email.length();
        while (start < end && isSpace(email.charAt(start))) {
            start++;
        }
        while (end > start && isSpace(email.charAt(end - 1))) {
            end--;
        }
        return start == end ? null : email.substring(start, end).toLowerCase(Locale.ROOT);
    }

    /** 공백·제어 문자(trim 기준), 유니코드 공백(strip 기준), 줄바꿈 없는 공백(U+00A0 등)을 모두 공백으로 본다. */
    private static boolean isSpace(char character) {
        return character <= ' ' || Character.isWhitespace(character) || Character.isSpaceChar(character);
    }
}
