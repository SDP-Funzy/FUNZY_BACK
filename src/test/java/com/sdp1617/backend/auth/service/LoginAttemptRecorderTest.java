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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 한도 판정 자체는 Lua 스크립트(Redis) 안에서 일어나므로, 여기서는 키 구성과 인자 전달,
 * 스크립트 결과 해석만 검증한다. 실제 동작은 로컬 Redis로 수동 검증.
 */
@ExtendWith(MockitoExtension.class)
class LoginAttemptRecorderTest {

    private static final String IP = "127.0.0.1";
    private static final List<String> KEYS = List.of(
            "login-fail:1:ip:" + IP, "login-fail:1:account", "login-fail:1:ips");

    @Mock
    private StringRedisTemplate redisTemplate;

    private LoginAttemptRecorder recorder;

    @BeforeEach
    void setUp() {
        recorder = new LoginAttemptRecorder(redisTemplate);
    }

    @Test
    @SuppressWarnings("unchecked")
    void 시도_예약은_IP_회원전체_IP집합_키와_한도를_함께_넘긴다() {
        when(redisTemplate.execute(any(RedisScript.class), eq(KEYS), any(), any(), any(), any())).thenReturn(1L);

        assertTrue(recorder.tryAcquire(1L, IP));

        verify(redisTemplate).execute(any(RedisScript.class), eq(KEYS),
                eq(String.valueOf(LoginAttemptRecorder.LOCK_DURATION.toSeconds())),
                eq(String.valueOf(LoginAttemptRecorder.MAX_ATTEMPTS_PER_IP)),
                eq(String.valueOf(LoginAttemptRecorder.MAX_ATTEMPTS_PER_ACCOUNT)),
                eq(IP));
    }

    @Test
    @SuppressWarnings("unchecked")
    void 스크립트가_0을_반환하면_잠긴_것으로_본다() {
        when(redisTemplate.execute(any(RedisScript.class), eq(KEYS), any(), any(), any(), any())).thenReturn(0L);

        assertFalse(recorder.tryAcquire(1L, IP));
    }

    @Test
    @SuppressWarnings("unchecked")
    void 로그인_성공은_그_IP_키로_기록을_정리한다() {
        recorder.recordSuccess(1L, IP);

        verify(redisTemplate).execute(any(RedisScript.class), eq(KEYS), eq(IP));
    }

    @Test
    @SuppressWarnings("unchecked")
    void clearAll은_IP_집합과_IP_키_접두사로_회원의_모든_기록을_지운다() {
        recorder.clearAll(1L);

        verify(redisTemplate).execute(any(RedisScript.class),
                eq(List.of("login-fail:1:account", "login-fail:1:ips")),
                eq("login-fail:1:ip:"));
    }
}
