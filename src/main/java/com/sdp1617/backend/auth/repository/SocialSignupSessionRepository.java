package com.sdp1617.backend.auth.repository;

import com.sdp1617.backend.auth.entity.AuthProvider;
import com.sdp1617.backend.auth.social.SocialSignupSession;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class SocialSignupSessionRepository {

    private static final String KEY_PREFIX = "social-signup:";
    private static final String DELIMITER = "|";

    private final StringRedisTemplate redisTemplate;

    public String issue(SocialSignupSession session, Duration ttl) {
        String token = UUID.randomUUID().toString();
        String value = session.provider().name() + DELIMITER
                + session.externalId() + DELIMITER
                + (session.email() == null ? "" : session.email());
        redisTemplate.opsForValue().set(key(token), value, ttl);
        return token;
    }

    public Optional<SocialSignupSession> consume(String token) {
        String value = redisTemplate.opsForValue().getAndDelete(key(token));
        if (value == null) {
            return Optional.empty();
        }

        String[] parts = value.split(Pattern.quote(DELIMITER), -1);
        // 저장할 때 null을 ""로 썼으므로 다시 null로 돌린다 (세션 생성자의 정규화에 기대지 않고 명시적으로)
        String email = parts[2].isEmpty() ? null : parts[2];
        return Optional.of(new SocialSignupSession(AuthProvider.valueOf(parts[0]), parts[1], email));
    }

    private String key(String token) {
        return KEY_PREFIX + token;
    }
}
