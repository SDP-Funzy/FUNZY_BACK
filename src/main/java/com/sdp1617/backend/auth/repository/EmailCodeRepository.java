package com.sdp1617.backend.auth.repository;

import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

/**
 * 회원가입 전 이메일 인증번호(6자리)를 Redis에 보관한다. (인증 완료 토큰은 {@link VerificationTokenRepository}가 담당)
 * - email-code:{email}           → 인증번호 (TTL = 인증번호 유효시간)
 * - email-code-attempts:{email}  → 틀린 횟수 (인증번호와 같은 TTL)
 * - email-code-cooldown:{email}  → 재발송 쿨다운 표시
 */
@Repository
@RequiredArgsConstructor
public class EmailCodeRepository {

    public enum VerifyResult { MATCHED, MISMATCHED, ATTEMPTS_EXCEEDED, EXPIRED }

    // 비교/시도 횟수 증가/폐기를 Redis 한 번의 명령으로 원자적으로 처리한다.
    // 따로 호출하면 동시 요청이 각자 "아직 한도 미만"을 보고 통과해 시도 횟수 제한을 우회할 수 있다.
    private static final RedisScript<Long> VERIFY = new DefaultRedisScript<>("""
            local stored = redis.call('GET', KEYS[1])
            if not stored then
                return 0
            end
            if stored == ARGV[1] then
                redis.call('DEL', KEYS[1], KEYS[2])
                return 1
            end
            local attempts = redis.call('INCR', KEYS[2])
            if attempts == 1 then
                redis.call('PEXPIRE', KEYS[2], redis.call('PTTL', KEYS[1]))
            end
            if attempts >= tonumber(ARGV[2]) then
                redis.call('DEL', KEYS[1], KEYS[2])
                return 3
            end
            return 2
            """, Long.class);

    // 이전 틀린 횟수 삭제와 새 인증번호 저장을 한 번에 처리한다. 따로 하면 그 사이에 들어온 오답 확인이
    // 이전 인증번호 기준으로 횟수를 올려, 새 인증번호가 시도 1회를 잃은 채로 시작할 수 있다.
    private static final RedisScript<Long> SAVE_CODE = new DefaultRedisScript<>("""
            redis.call('DEL', KEYS[2])
            redis.call('SET', KEYS[1], ARGV[1], 'PX', ARGV[2])
            return 1
            """, Long.class);

    private final StringRedisTemplate redisTemplate;

    /** 쿨다운 중이 아니면 쿨다운을 시작하고 true, 이미 쿨다운 중이면 false. */
    public boolean tryStartCooldown(String email, Duration cooldown) {
        Boolean started = redisTemplate.opsForValue().setIfAbsent(cooldownKey(email), "1", cooldown);
        return Boolean.TRUE.equals(started);
    }

    public void clearCooldown(String email) {
        redisTemplate.delete(cooldownKey(email));
    }

    /** 새 인증번호를 저장한다. 이전 인증번호와 틀린 횟수는 함께 초기화된다. */
    public void saveCode(String email, String code, Duration ttl) {
        redisTemplate.execute(SAVE_CODE, List.of(codeKey(email), attemptsKey(email)),
                code, String.valueOf(ttl.toMillis()));
    }

    public VerifyResult verify(String email, String code, int maxAttempts) {
        Long result = redisTemplate.execute(
                VERIFY, List.of(codeKey(email), attemptsKey(email)), code, String.valueOf(maxAttempts));
        if (result == null) {
            return VerifyResult.EXPIRED;
        }
        return switch (result.intValue()) {
            case 1 -> VerifyResult.MATCHED;
            case 2 -> VerifyResult.MISMATCHED;
            case 3 -> VerifyResult.ATTEMPTS_EXCEEDED;
            default -> VerifyResult.EXPIRED;
        };
    }

    private String codeKey(String email) {
        return "email-code:" + email;
    }

    private String attemptsKey(String email) {
        return "email-code-attempts:" + email;
    }

    private String cooldownKey(String email) {
        return "email-code-cooldown:" + email;
    }
}
