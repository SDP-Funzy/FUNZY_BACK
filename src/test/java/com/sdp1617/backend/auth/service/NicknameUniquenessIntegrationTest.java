package com.sdp1617.backend.auth.service;

import com.sdp1617.backend.auth.entity.Consent;
import com.sdp1617.backend.auth.entity.Member;
import com.sdp1617.backend.global.error.CustomException;
import com.sdp1617.backend.global.error.ErrorCode;
import com.sdp1617.backend.mypage.dto.NicknameUpdateRequest;
import com.sdp1617.backend.mypage.service.ProfileService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 아이디 중복은 대소문자를 구분하지 않는다 (#144). 규칙 전에 대문자로 가입한 "LegacyTiki"가 있으면
 * 새 회원은 "legacytiki"를 쓸 수 없고, 그 회원 본인은 소문자로 바꿀 수 있다. 실제 DB로 검증 (CI에서는 PostgreSQL).
 */
@SpringBootTest
@Transactional
class NicknameUniquenessIntegrationTest {

    @Autowired private EntityManager em;
    @Autowired private AuthService authService;
    @Autowired private ProfileService profileService;

    private Member legacy;

    private Member member(String nickname) {
        // 요청 검증을 거치지 않고 저장 — 규칙 전에 가입한 회원
        Member member = new Member(nickname.toLowerCase() + "@unique.test", "encoded", nickname, Consent.requiredOnly());
        em.persist(member);
        return member;
    }

    @BeforeEach
    void setUp() {
        legacy = member("LegacyTiki");
        em.flush();
    }

    @Test
    void 대소문자만_다른_아이디는_이미_사용_중이다() {
        assertFalse(authService.isNicknameAvailable("legacytiki"));
        assertTrue(authService.isNicknameAvailable("legacytiki2"));
    }

    @Test
    void 아이디_변경_화면의_중복_확인은_변경_API와_같이_본인을_빼고_확인한다() {
        Member other = member("othermember");
        em.flush();

        assertTrue(authService.isNicknameAvailable("legacytiki", legacy.getId()));   // 본인의 대소문자 변경
        assertFalse(authService.isNicknameAvailable("legacytiki", other.getId()));   // 다른 회원
        assertFalse(authService.isNicknameAvailable("legacytiki", null));            // 로그인 전(가입 화면)
    }

    @Test
    void 다른_회원은_대소문자만_다른_아이디로_바꿀_수_없다() {
        Member other = member("othermember");
        em.flush();

        CustomException exception = assertThrows(CustomException.class,
                () -> profileService.updateNickname(other.getId(), new NicknameUpdateRequest("legacytiki")));

        assertEquals(ErrorCode.AUTH_007, exception.getErrorCode());
    }

    @Test
    void 대문자_아이디를_가진_회원_본인은_소문자로_바꿀_수_있다() {
        profileService.updateNickname(legacy.getId(), new NicknameUpdateRequest("legacytiki"));
        em.flush();
        em.clear();

        assertEquals("legacytiki", em.find(Member.class, legacy.getId()).getNickname());
    }
}
