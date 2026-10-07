package com.sdp1617.backend.social.service;

import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * 회원 검색 요청 수 제한. 닉네임은 로그인 아이디라, 검색을 자동으로 반복해 아이디 목록을 대량으로 모으는 것을 막는다.
 * Redis 장애 시에는 제한 없이 허용한다 — 검색을 막는 것보다 서비스가 계속 동작하는 편이 낫고, 로그인은 별도로 잠긴다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MemberSearchRateLimiter {

    static final int MAX_REQUESTS = 30;
    static final Duration WINDOW = Duration.ofMinutes(1);
    private static final String KEY_PREFIX = "member-search:";

    // INCR과 EXPIRE를 따로 호출하면 그 사이 프로세스가 죽었을 때 TTL 없는 키가 남아 영구 차단될 수 있다
    private static final RedisScript<Long> INCREMENT_WITH_EXPIRE = new DefaultRedisScript<>("""
            local current = redis.call('INCR', KEYS[1])
            if current == 1 then
                redis.call('EXPIRE', KEYS[1], ARGV[1])
            end
            return current
            """, Long.class);

    private final StringRedisTemplate redisTemplate;

    public boolean tryAcquire(Long memberId) {
        try {
            Long count = redisTemplate.execute(
                    INCREMENT_WITH_EXPIRE, List.of(KEY_PREFIX + memberId), String.valueOf(WINDOW.toSeconds()));
            return count == null || count <= MAX_REQUESTS;
        } catch (DataAccessException exception) {
            log.warn("회원 검색 요청 수 제한 확인 실패 — 제한 없이 허용: {}", exception.getClass().getSimpleName());
            return true;
        }
    }
}
