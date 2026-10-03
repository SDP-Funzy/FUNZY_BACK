package com.sdp1617.backend.auth.social;

import com.sdp1617.backend.auth.entity.AuthProvider;
import com.sdp1617.backend.auth.util.Emails;

public record SocialSignupSession(
        AuthProvider provider,
        String externalId,
        String email
) {
    /** provider가 준 이메일도 이메일 가입/조회와 같은 규칙으로 정규화한다 ({@link Emails}). */
    public SocialSignupSession {
        email = Emails.normalize(email);
    }
}
