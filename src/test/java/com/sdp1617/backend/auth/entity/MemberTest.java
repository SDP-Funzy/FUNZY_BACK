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

    @Test
    void 이메일은_어떤_경로로_생성돼도_정규화되어_저장된다() {
        // 호출하는 쪽이 정규화를 빠뜨려도 대소문자만 다른 중복 계정이 생기지 않도록 엔티티가 보장한다
        Member local = new Member(" Local@Gmail.COM ", "encoded", "닉네임", Consent.requiredOnly());
        Member social = new Member("Social@Kakao.com", "소셜닉네임", Consent.requiredOnly(), AuthProvider.KAKAO, "12345");

        assertEquals("local@gmail.com", local.getEmail());
        assertEquals("social@kakao.com", social.getEmail());
    }

    @Test
    void 아이디_비밀번호_회원은_이메일이_비어있으면_만들_수_없다() {
        // 이메일이 없으면 비밀번호 재설정·아이디 찾기·잠금 해제를 할 수 없는 계정이 된다
        assertThrows(IllegalArgumentException.class,
                () -> new Member("   ", "encoded", "닉네임", Consent.requiredOnly()));
    }
}
