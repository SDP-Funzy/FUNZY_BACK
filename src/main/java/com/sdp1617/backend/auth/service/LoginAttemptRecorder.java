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
 * 로그인 시도 횟수를 Redis에 세서, 한도를 넘으면 잠근다. 두 단계로 센다.
 * - 회원 + IP: 같은 IP에서 5회 → 그 IP만 15분 잠금
 * - 회원 전체: 모든 IP 합산 30회 → 그 계정 전체 15분 잠금
 *
 * 로그인 아이디(닉네임)는 다른 사용자에게 공개되는 값이라, 낮은 한도로 회원 전체를 잠그면 닉네임만 아는 누구나
 * 남의 계정을 계속 잠가둘 수 있다. 그래서 1차 잠금은 IP 단위로 두어 공격자가 자기 IP만 잠그게 하고,
 * IP를 바꿔가며 무제한으로 비밀번호를 대입하는 것은 넉넉한 회원 전체 한도로 막는다.
 * IPv6는 한 사용자가 /64 대역 전체를 쓸 수 있어 주소 하나하나가 아니라 /64 단위로 센다({@link ClientIps}).
 *
 * 시도는 비밀번호 검사 "전에" 원자적으로 예약한다({@link #tryAcquire}). 검사 후에 세면 동시에 들어온
 * 요청들이 모두 "아직 한도 미만"을 보고 bcrypt 검사까지 통과해 한도를 넘는 횟수만큼 대입할 수 있다.
 * 성공하면 {@link #recordSuccess}로 그 IP의 기록을 지우고, 실패는 예약된 그대로 남는다.
 */
@Component
@RequiredArgsConstructor
public class LoginAttemptRecorder {

    static final int MAX_ATTEMPTS_PER_IP = 5;
    static final int MAX_ATTEMPTS_PER_ACCOUNT = 30;
    static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    // KEYS: 1=IP 카운터, 2=회원 전체 카운터, 3=시도한 IP 집합 / ARGV: 1=TTL(초), 2=IP 한도, 3=회원 한도, 4=IP 키
    // 한도에 걸리면 세지 않고 0을 반환한다. 첫 시도 시 15분 구간을 열고, 한도에 도달하는 순간 TTL을
    // 다시 15분으로 늘려 마지막 시도부터 온전히 15분간 잠기게 한다.
    private static final RedisScript<Long> TRY_ACQUIRE = new DefaultRedisScript<>("""
            local ipCount = tonumber(redis.call('GET', KEYS[1]) or '0')
            local accountCount = tonumber(redis.call('GET', KEYS[2]) or '0')
            if ipCount >= tonumber(ARGV[2]) or accountCount >= tonumber(ARGV[3]) then
                return 0
            end
            ipCount = redis.call('INCR', KEYS[1])
            if ipCount == 1 or ipCount == tonumber(ARGV[2]) then
                redis.call('EXPIRE', KEYS[1], ARGV[1])
            end
            accountCount = redis.call('INCR', KEYS[2])
            if accountCount == 1 or accountCount == tonumber(ARGV[3]) then
                redis.call('EXPIRE', KEYS[2], ARGV[1])
            end
            redis.call('SADD', KEYS[3], ARGV[4])
            redis.call('EXPIRE', KEYS[3], ARGV[1])
            return 1
            """, Long.class);

    // 성공 시 그 IP의 기록을 지우고, 회원 전체 카운터에서는 방금 성공한 1회 예약만 돌려준다.
    // (정상 사용자가 여러 기기에서 자주 로그인해도 회원 전체 한도에 쌓이지 않게)
    // IP 몫을 통째로 빼면, 두 키의 만료 시점이 어긋났을 때 다른 IP에서 쌓인 실패까지 지워질 수 있다.
    // 성공 전 같은 IP에서 틀린 몇 번은 회원 전체 카운터에 남지만, 15분 뒤 자연히 사라진다.
    private static final RedisScript<Long> RECORD_SUCCESS = new DefaultRedisScript<>("""
            redis.call('DEL', KEYS[1])
            redis.call('SREM', KEYS[3], ARGV[1])
            if redis.call('EXISTS', KEYS[2]) == 1 then
                if redis.call('DECR', KEYS[2]) <= 0 then
                    redis.call('DEL', KEYS[2])
                end
            end
            return 1
            """, Long.class);

    // 시도한 IP 집합으로 지울 키를 찾는다 (전체 키 SCAN 없이). 키 이름을 스크립트 안에서 만들므로
    // Redis Cluster에서는 쓸 수 없다 — 현재는 단일 Redis 인스턴스 구성.
    private static final RedisScript<Long> CLEAR_ALL = new DefaultRedisScript<>("""
            local ips = redis.call('SMEMBERS', KEYS[2])
            for _, ip in ipairs(ips) do
                redis.call('DEL', ARGV[1] .. ip)
            end
            redis.call('DEL', KEYS[1], KEYS[2])
            return #ips
            """, Long.class);

    private final StringRedisTemplate redisTemplate;

    /** 시도를 예약한다. 이 IP 또는 이 계정이 잠긴 상태면 false (세지 않음). */
    public boolean tryAcquire(Long memberId, String clientIp) {
        String ipKey = ClientIps.rateLimitKey(clientIp);
        Long result = redisTemplate.execute(TRY_ACQUIRE, keys(memberId, ipKey),
                String.valueOf(LOCK_DURATION.toSeconds()),
                String.valueOf(MAX_ATTEMPTS_PER_IP),
                String.valueOf(MAX_ATTEMPTS_PER_ACCOUNT),
                ipKey);
        return result != null && result == 1L;
    }

    public void recordSuccess(Long memberId, String clientIp) {
        String ipKey = ClientIps.rateLimitKey(clientIp);
        redisTemplate.execute(RECORD_SUCCESS, keys(memberId, ipKey), ipKey);
    }

    /** 잠금 해제/비밀번호 재설정처럼 본인 확인을 거친 경우 모든 IP와 회원 전체 기록을 지운다. */
    public void clearAll(Long memberId) {
        redisTemplate.execute(CLEAR_ALL, List.of(prefix(memberId) + "account", prefix(memberId) + "ips"),
                ipKeyPrefix(memberId));
    }

    private List<String> keys(Long memberId, String ipKey) {
        return List.of(ipKeyPrefix(memberId) + ipKey, prefix(memberId) + "account", prefix(memberId) + "ips");
    }

    private String prefix(Long memberId) {
        return "login-fail:" + memberId + ":";
    }

    private String ipKeyPrefix(Long memberId) {
        return prefix(memberId) + "ip:";
    }
}
