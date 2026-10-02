package com.sdp1617.backend.auth.service;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VerificationRequestRateLimiterTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    private VerificationRequestRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        rateLimiter = new VerificationRequestRateLimiter(redisTemplate);
    }

    @SuppressWarnings("unchecked")
    private void stubIncrementResult(String key, long result) {
        when(redisTemplate.execute(any(RedisScript.class), eq(List.of(key)), any())).thenReturn(result);
    }

    @Test
    void 한도_이내면_허용한다() {
        stubIncrementResult("rate-limit:password-reset:ip:127.0.0.1", 1L);
        stubIncrementResult("rate-limit:password-reset:email:test@sdp1617.com", 1L);

        boolean allowed = rateLimiter.isAllowed("password-reset", "127.0.0.1", "test@sdp1617.com");

        assertTrue(allowed);
    }

    @Test
    void 이메일_기준_한도를_초과하면_거부한다() {
        stubIncrementResult("rate-limit:password-reset:ip:127.0.0.1", 1L);
        stubIncrementResult("rate-limit:password-reset:email:test@sdp1617.com", 6L);

        boolean allowed = rateLimiter.isAllowed("password-reset", "127.0.0.1", "test@sdp1617.com");

        assertFalse(allowed);
    }

    @Test
    @SuppressWarnings("unchecked")
    void IP_기준_한도를_초과하면_이메일_카운터는_건드리지_않고_거부한다() {
        stubIncrementResult("rate-limit:password-reset:ip:127.0.0.1", 21L);

        boolean allowed = rateLimiter.isAllowed("password-reset", "127.0.0.1", "test@sdp1617.com");

        assertFalse(allowed);
        verify(redisTemplate, never())
                .execute(any(RedisScript.class), eq(List.of("rate-limit:password-reset:email:test@sdp1617.com")), any());
    }

    @Test
    void purpose가_다르면_서로_다른_키를_사용한다() {
        stubIncrementResult("rate-limit:account-unlock:ip:127.0.0.1", 1L);
        stubIncrementResult("rate-limit:account-unlock:email:test@sdp1617.com", 1L);

        boolean allowed = rateLimiter.isAllowed("account-unlock", "127.0.0.1", "test@sdp1617.com");

        assertTrue(allowed);
    }

    @Test
    @SuppressWarnings("unchecked")
    void Redis_결과가_null이면_거부한다() {
        when(redisTemplate.execute(any(RedisScript.class), eq(List.of("rate-limit:password-reset:ip:127.0.0.1")), any()))
                .thenReturn(null);

        boolean allowed = rateLimiter.isAllowed("password-reset", "127.0.0.1", "test@sdp1617.com");

        assertFalse(allowed);
    }

    @Test
    @SuppressWarnings("unchecked")
    void IP_예약은_한도와_함께_스크립트에_넘기고_결과로_허용_여부를_판단한다() {
        when(redisTemplate.execute(any(RedisScript.class), eq(List.of("rate-limit:email-code-verify:ip:127.0.0.1")),
                eq("600"), eq("20"))).thenReturn(1L, 0L);

        assertTrue(rateLimiter.tryReserveForIp("email-code-verify", "127.0.0.1"));
        assertFalse(rateLimiter.tryReserveForIp("email-code-verify", "127.0.0.1"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void IP_예약_반환은_같은_키로_스크립트를_실행한다() {
        rateLimiter.releaseForIp("email-code-verify", "127.0.0.1");

        verify(redisTemplate).execute(any(RedisScript.class), eq(List.of("rate-limit:email-code-verify:ip:127.0.0.1")));
    }

    @Test
    @SuppressWarnings("unchecked")
    void 이메일당_하루_한도는_하루_TTL로_별도_키에_센다() {
        when(redisTemplate.execute(any(RedisScript.class), eq(List.of("rate-limit:email-code:email-daily:test@sdp1617.com")),
                eq("86400"))).thenReturn(20L, 21L);

        assertTrue(rateLimiter.isAllowedDaily("email-code", "test@sdp1617.com"));
        assertFalse(rateLimiter.isAllowedDaily("email-code", "test@sdp1617.com"));
    }
}
