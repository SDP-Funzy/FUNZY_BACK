package com.sdp1617.backend.auth.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemberTest {

    @Test
    void 이메일_가입_회원은_비밀번호를_가진다() {
        Member member = new Member("test@sdp1617.com", "encoded", "닉네임", Consent.requiredOnly());

        assertTrue(member.hasPassword());
    }

    @Test
    void 소셜_가입_회원은_비밀번호가_없다() {
        Member member = new Member("test@kakao.com", "닉네임", Consent.requiredOnly(), AuthProvider.KAKAO, "12345");

        assertFalse(member.hasPassword());
        assertEquals(AuthProvider.KAKAO, member.getProvider());
        assertEquals("12345", member.getProviderId());
    }

    @Test
    void 소셜_가입_회원은_providerId가_없으면_예외를_던진다() {
        assertThrows(IllegalArgumentException.class,
                () -> new Member("test@kakao.com", "닉네임", Consent.requiredOnly(), AuthProvider.KAKAO, null));
        assertThrows(IllegalArgumentException.class,
                () -> new Member("test@kakao.com", "닉네임", Consent.requiredOnly(), AuthProvider.KAKAO, ""));
    }

    @Test
    void 소셜_가입_생성자에_LOCAL_provider를_넘기면_예외를_던진다() {
        assertThrows(IllegalArgumentException.class,
                () -> new Member("test@sdp1617.com", "닉네임", Consent.requiredOnly(), AuthProvider.LOCAL, "12345"));
    }

    @Test
    void 팔로우코드를_재발급하면_값이_바뀐다() {
        Member member = new Member("test@sdp1617.com", "encoded", "닉네임", Consent.requiredOnly());
        member.reissueFollowCode();
        String firstCode = member.getFollowCode();

        member.reissueFollowCode();

        assertNotEquals(firstCode, member.getFollowCode());
    }

    @Test
    void 가입시_푸시_알림_수신은_기본으로_켜져있다() {
        Member member = new Member("test@sdp1617.com", "encoded", "닉네임", Consent.requiredOnly());

        assertTrue(member.isPushNotificationEnabled());
    }

    @Test
    void 푸시_알림_수신_설정을_변경할_수_있다() {
        Member member = new Member("test@sdp1617.com", "encoded", "닉네임", Consent.requiredOnly());

        member.updatePushNotificationEnabled(false);

        assertFalse(member.isPushNotificationEnabled());
    }

    @Test
    void 이메일_가입_회원은_전달받은_동의정보를_그대로_저장한다() {
        Consent consent = new Consent(true, true, true, false);

        Member member = new Member("test@sdp1617.com", "encoded", "닉네임", consent);

        assertEquals(consent, member.getConsent());
    }

    @Test
    void 소셜_가입_회원은_전달받은_동의정보를_그대로_저장한다() {
        Consent consent = new Consent(true, true, false, true);

        Member member = new Member("test@kakao.com", "닉네임", consent, AuthProvider.KAKAO, "12345");

        assertEquals(consent, member.getConsent());
    }
}
