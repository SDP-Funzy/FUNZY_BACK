package com.sdp1617.backend.auth.util;

import com.sdp1617.backend.auth.entity.AuthProvider;
import com.sdp1617.backend.auth.social.SocialSignupSession;
import com.sdp1617.backend.auth.social.SocialUserInfo;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class EmailsTest {

    @Test
    void 앞뒤_공백을_지우고_소문자로_바꾼다() {
        assertEquals("john.doe@gmail.com", Emails.normalize("  John.Doe@Gmail.COM "));
    }

    @Test
    void null이나_공백뿐인_값은_null로_돌려준다() {
        assertNull(Emails.normalize(null));
        assertNull(Emails.normalize("   "));
        assertNull(Emails.normalize("\t\n"));
        assertNull(Emails.normalize("\u2003"));
    }

    @Test
    void 유니코드_공백도_앞뒤에서_지운다() {
        assertEquals("a@b.com", Emails.normalize("\u2003A@B.com\u2003"));
        // 복사·붙여넣기로 섞이기 쉬운 줄바꿈 없는 공백(NBSP)
        assertEquals("a@b.com", Emails.normalize("\u00A0A@B.com\u202F"));
        assertNull(Emails.normalize("\u00A0"));
    }

    @Test
    void 소셜_provider가_준_이메일도_같은_규칙으로_정규화된다() {
        assertEquals("john.doe@gmail.com", new SocialUserInfo("1", "John.Doe@Gmail.com").email());
        assertEquals("john.doe@gmail.com",
                new SocialSignupSession(AuthProvider.GOOGLE, "1", "John.Doe@Gmail.com").email());
        assertNull(new SocialUserInfo("1", "").email());
    }
}
