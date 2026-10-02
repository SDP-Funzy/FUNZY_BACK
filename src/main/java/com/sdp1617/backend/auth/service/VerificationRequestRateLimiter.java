package com.sdp1617.backend.auth.service;

import com.sdp1617.backend.global.common.ClientIps;
import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * 비밀번호 재설정/계정 잠금 해제/가입 인증번호 발송처럼 인증 없이 메일을 보내는 엔드포인트를 위한
 * 고정 윈도(fixed window) rate limiter (Redis INCR + 최초 호출 시 TTL 설정).
 * IP만 보면 여러 이메일을 순회하는 공격을, 이메일만 보면 여러 IP로 분산된 공격을 못 막아서 둘 다 확인한다.
 * IP 제한은 캠퍼스 Wi-Fi/사내망처럼 여러 사용자가 한 IP를 공유하는 상황을 고려해 이메일 제한보다 넉넉하게 둔다.
 *
 * 알려진 한계: 이메일 기준 제한은 공격자가 피해자의 이메일 주소만 알아도(비밀값 아님) 그 계정으로
 * 한도(MAX_REQUESTS_PER_EMAIL)만큼 먼저 요청을 소진시켜, 정작 피해자 본인은 윈도가 끝날 때까지
 * 최대 WINDOW만큼 메일을 못 받게 만들 수 있다(항상 200을 반환하므로 실패 여부도 드러나지 않음).
 * 다만 이 피해는 일시적(윈도 종료 시 자동 해제)이고 데이터 유출이 없어, 이 rate limiter가 막으려던
 * 원래 문제(무제한 자원 고갈·메일 스팸, 이슈 #56)보다 훨씬 가벼운 트레이드오프로 보고 의도적으로 감수한다.
 * 완전히 막으려면 CAPTCHA 등 사람 확인 절차가 필요한데, 이는 스코프를 넘어서 별도 이슈로 다룬다.
 */
@Component
@RequiredArgsConstructor
public class VerificationRequestRateLimiter {

    private static final int MAX_REQUESTS_PER_IP = 20;
    private static final int MAX_REQUESTS_PER_EMAIL = 5;
    private static final Duration WINDOW = Duration.ofMinutes(10);
    private static final int MAX_DAILY_REQUESTS_PER_EMAIL = 20;
    private static final Duration DAILY_WINDOW = Duration.ofDays(1);

    // INCR과 (최초 요청일 때만) EXPIRE 설정을 Redis 한 번의 명령으로 원자적으로 처리한다.
    // 두 명령을 따로 호출하면 그 사이 프로세스가 죽었을 때 TTL 없는 키가 영구히 남아
    // 해당 IP/이메일이 영영 차단될 수 있다.
    private static final RedisScript<Long> INCREMENT_WITH_EXPIRE = new DefaultRedisScript<>("""
            local current = redis.call('INCR', KEYS[1])
            if current == 1 then
                redis.call('EXPIRE', KEYS[1], ARGV[1])
            end
            return current
            """, Long.class);

    // 한도 미만일 때만 1 증가시키고 1을 반환, 한도에 도달해 있으면 세지 않고 0을 반환한다.
    private static final RedisScript<Long> RESERVE_IF_BELOW_LIMIT = new DefaultRedisScript<>("""
            local current = tonumber(redis.call('GET', KEYS[1]) or '0')
            if current >= tonumber(ARGV[2]) then
                return 0
            end
            if redis.call('INCR', KEYS[1]) == 1 then
                redis.call('EXPIRE', KEYS[1], ARGV[1])
            end
            return 1
            """, Long.class);

    // 예약을 돌려준다. 그 사이 키가 만료됐으면 아무것도 하지 않는다 (DECR이 TTL 없는 -1 키를 만들지 않도록).
    private static final RedisScript<Long> RELEASE = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[1]) == 1 and tonumber(redis.call('GET', KEYS[1])) > 0 then
                return redis.call('DECR', KEYS[1])
            end
            return 0
            """, Long.class);

    private final StringRedisTemplate redisTemplate;

    public boolean isAllowed(String purpose, String clientIp, String email) {
        return tryAcquire(ipKey(purpose, clientIp), MAX_REQUESTS_PER_IP)
                && tryAcquire("rate-limit:" + purpose + ":email:" + email, MAX_REQUESTS_PER_EMAIL);
    }

    /**
     * 실패한 요청만 세는 IP 단독 제한(예: 인증번호 확인)용. 성공까지 세면 캠퍼스 Wi-Fi처럼 IP를 공유하는
     * 정상 사용자들끼리 한도를 나눠 쓰다 서로 막히므로, 처리 전에 {@link #tryReserveForIp}로 1회를 원자적으로
     * 예약해두고, 실패가 아니면 {@link #releaseForIp}로 돌려준다. 처리 후에 세면 동시에 들어온 요청들이 모두
     * "아직 한도 미만"을 보고 통과해 한도를 넘길 수 있다. 이메일 기준으로 세면 오히려 피해자를 막는 데 악용될 수 있다.
     */
    public boolean tryReserveForIp(String purpose, String clientIp) {
        Long reserved = redisTemplate.execute(RESERVE_IF_BELOW_LIMIT, List.of(ipKey(purpose, clientIp)),
                String.valueOf(WINDOW.toSeconds()), String.valueOf(MAX_REQUESTS_PER_IP));
        return reserved != null && reserved == 1L;
    }

    public void releaseForIp(String purpose, String clientIp) {
        redisTemplate.execute(RELEASE, List.of(ipKey(purpose, clientIp)));
    }

    private String ipKey(String purpose, String clientIp) {
        return "rate-limit:" + purpose + ":ip:" + ClientIps.rateLimitKey(clientIp);
    }

    /**
     * 이메일당 하루 한도. 10분 한도만 있으면 오래 버티는 추측 공격에 상한이 없다 — 예: 인증번호는 10분에
     * 5번 발송 × 번호당 5번 추측 = 25번이라, 하루면 3,600번 추측할 수 있다. 하루 한도로 장기 추측량을 묶는다.
     */
    public boolean isAllowedDaily(String purpose, String email) {
        return tryAcquire("rate-limit:" + purpose + ":email-daily:" + email, MAX_DAILY_REQUESTS_PER_EMAIL, DAILY_WINDOW);
    }

    private boolean tryAcquire(String key, int maxRequests) {
        return tryAcquire(key, maxRequests, WINDOW);
    }

    private boolean tryAcquire(String key, int maxRequests, Duration window) {
        Long count = redisTemplate.execute(INCREMENT_WITH_EXPIRE, List.of(key), String.valueOf(window.toSeconds()));
        return count != null && count <= maxRequests;
    }
}
