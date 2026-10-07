package com.sdp1617.backend.social.service;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemberSearchRateLimiterTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @InjectMocks
    private MemberSearchRateLimiter rateLimiter;

    @SuppressWarnings("unchecked")
    private void countIs(long count) {
        when(redisTemplate.execute(any(RedisScript.class), eq(List.of("member-search:1")), anyString()))
                .thenReturn(count);
    }

    @Test
    void 한도까지는_허용한다() {
        countIs(MemberSearchRateLimiter.MAX_REQUESTS);

        assertTrue(rateLimiter.tryAcquire(1L));
    }

    @Test
    void 한도를_넘으면_거절한다() {
        countIs(MemberSearchRateLimiter.MAX_REQUESTS + 1);

        assertFalse(rateLimiter.tryAcquire(1L));
    }

    @Test
    @SuppressWarnings("unchecked")
    void Redis_장애_시에는_허용한다() {
        when(redisTemplate.execute(any(RedisScript.class), eq(List.of("member-search:1")), anyString()))
                .thenThrow(new RedisConnectionFailureException("down"));

        assertTrue(rateLimiter.tryAcquire(1L));
    }
}
