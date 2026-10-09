package com.sdp1617.backend.auth.dto;

import com.sdp1617.backend.mypage.dto.NicknameUpdateRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 아이디(닉네임) 규칙 (H-121, #144): 2~20자, 영문 소문자·숫자·마침표·밑줄, 마침표로 시작·끝·연속 불가. */
class NicknamePolicyTest {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    private static Set<ConstraintViolation<NicknameUpdateRequest>> validate(String nickname) {
        return VALIDATOR.validate(new NicknameUpdateRequest(nickname));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ab", "tiki", "tiki_kim", "tiki.kim", "t.i.k.i", "_tiki_", "tiki2026", "a1",
            "abcdefghijklmnopqrst"})
    void 규칙에_맞는_아이디는_허용한다(String nickname) {
        assertTrue(validate(nickname).isEmpty(), nickname);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "a",                       // 1자
            "abcdefghijklmnopqrstu",   // 21자
            "Tiki",                    // 대문자는 거절 (소문자로 바꾸지 않음)
            "티키",                     // 한글
            "tiki kim",                // 띄어쓰기
            "tiki-kim",                // 하이픈
            "tiki!",                   // 특수문자
            "tiki😀",                  // 이모지
            ".tiki",                   // 마침표로 시작
            "tiki.",                   // 마침표로 끝
            "ti..ki",                  // 마침표 연속
            "탈퇴한회원1"                // 탈퇴 회원 예약 닉네임은 규칙상 만들 수 없음
    })
    void 규칙에_어긋나는_아이디는_거절하고_규칙을_안내한다(String nickname) {
        Set<ConstraintViolation<NicknameUpdateRequest>> violations = validate(nickname);

        assertEquals(1, violations.size(), nickname);
        assertEquals(NicknamePolicy.MESSAGE, violations.iterator().next().getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void 빈_아이디는_항상_입력_안내_하나만_나온다(String nickname) {
        Set<ConstraintViolation<NicknameUpdateRequest>> violations = validate(nickname);

        assertEquals(1, violations.size());
        assertEquals(NicknamePolicy.REQUIRED_MESSAGE, violations.iterator().next().getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"Tiki", ".tiki", "티키"})
    void 회원가입과_소셜_가입에도_같은_규칙을_쓴다(String nickname) {
        assertTrue(VALIDATOR.validate(new SignUpRequest("token", "Password1!", "Password1!", nickname, true, true, false, false))
                .stream().anyMatch(v -> v.getMessage().equals(NicknamePolicy.MESSAGE)));
        assertTrue(VALIDATOR.validate(new SocialSignUpCompleteRequest("token", nickname, true, true, false, false))
                .stream().anyMatch(v -> v.getMessage().equals(NicknamePolicy.MESSAGE)));
    }
}
