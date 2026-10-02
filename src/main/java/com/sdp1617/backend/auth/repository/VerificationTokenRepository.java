package com.sdp1617.backend.auth.repository;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class VerificationTokenRepository {

    private final StringRedisTemplate redisTemplate;

    public String issue(String purpose, Long memberId, Duration ttl) {
        return issueValue(purpose, String.valueOf(memberId), ttl);
    }

    public Optional<Long> consume(String purpose, String token) {
        String value = redisTemplate.opsForValue().getAndDelete(key(purpose, token));
        return Optional.ofNullable(value).map(Long::valueOf);
    }

    /** 회원 id가 아닌 값(예: 가입 전 인증된 이메일)을 담는 토큰을 발급한다. */
    public String issueValue(String purpose, String value, Duration ttl) {
        String token = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(key(purpose, token), value, ttl);
        return token;
    }

    /** 토큰을 소비하지 않고 값만 조회한다. 작업이 성공한 뒤 {@link #delete}로 직접 폐기해야 한다. */
    public Optional<String> findValue(String purpose, String token) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(key(purpose, token)));
    }

    public void delete(String purpose, String token) {
        redisTemplate.delete(key(purpose, token));
    }

    private String key(String purpose, String token) {
        return purpose + ":" + token;
    }
}
