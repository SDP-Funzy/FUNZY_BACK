package com.sdp1617.backend.auth.repository;

import com.sdp1617.backend.auth.jwt.JwtProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * refresh token 세션 저장소. 로그인 한 번이 하나의 "체인"(OAuth의 token family)이 되고, 재발급할 때마다
 * 체인 안에서 토큰이 교체된다(rotation). tokenId는 "체인ID.고유값" 형식이라 어떤 토큰이든 자기 체인을 알 수 있다.
 *
 * Redis 키
 * - refresh-token:{memberId}:{tokenId}           살아 있는 세션 (체인당 최대 1개)
 * - refresh-token-sessions:{memberId}             회원의 살아 있는 tokenId 목록 (전체 폐기용)
 * - refresh-token-chain:{memberId}:{chainId}      체인의 현재 tokenId
 * - refresh-token-retired:{memberId}:{chainId}    최근 유예 시간 안에 교체된 tokenId → 교체 시각(ms) (ZSET)
 *
 * 체인 기록은 로그인당 하나라, 재발급마다 키가 쌓이지 않는다. 시각은 앱 서버가 아닌 Redis 시계(TIME)를 쓴다.
 */
@Repository
@RequiredArgsConstructor
public class RefreshTokenRepository {

    private static final String KEY_PREFIX = "refresh-token:";
    private static final String SESSIONS_KEY_PREFIX = "refresh-token-sessions:";
    private static final String CHAIN_KEY_PREFIX = "refresh-token-chain:";
    private static final String RETIRED_KEY_PREFIX = "refresh-token-retired:";
    private static final String VALID_MARKER = "valid";
    private static final String CHAIN_SEPARATOR = ".";

    // KEYS: 1=세션, 2=세션 목록, 3=체인 / ARGV: 1=세션 값, 2=TTL(ms), 3=tokenId
    private static final RedisScript<Long> SAVE_SCRIPT = new DefaultRedisScript<>("""
            redis.call('SET', KEYS[1], ARGV[1], 'PX', ARGV[2])
            redis.call('SADD', KEYS[2], ARGV[3])
            redis.call('PEXPIRE', KEYS[2], ARGV[2])
            redis.call('SET', KEYS[3], ARGV[3], 'PX', ARGV[2])
            return 1
            """, Long.class);

    /**
     * 체인 단위 교체. 제시된 토큰이
     * - 살아 있는 현재 토큰이면 → 새 토큰으로 교체하고, 제시된 토큰을 "최근 교체" 목록에 교체 시각과 함께 남긴다 (ROTATED)
     * - 유예 시간 안에 교체된 토큰이면 → 앱이 동시에 여러 번 재발급한 경우로 보고 현재 토큰을 다시 내준다 (GRACE_RETRY)
     *   유예는 "최근 교체 목록"에 있는 토큰에만 준다. 체인이 방금 교체됐다고 해서 오래전 토큰까지 받아주면,
     *   훔쳐둔 옛 토큰을 그 틈에 끼워 넣어 현재 토큰을 가로챌 수 있기 때문이다.
     * - 그 밖의 예전 토큰이면 → 교체된 토큰의 재사용 = 탈취 의심으로 보고 그 체인을 폐기한다 (REUSED).
     *   다른 기기(다른 체인)는 건드리지 않는다.
     * - 체인이 없거나 이미 폐기됐으면 → NOT_FOUND (로그아웃·비밀번호 변경 등 이후의 옛 토큰은 다른 세션에 영향 없이 거절)
     *
     * KEYS: 1=제시된 세션, 2=세션 목록, 3=새 세션, 4=체인, 5=최근 교체 목록
     * ARGV: 1=세션 값, 2=TTL(ms), 3=제시된 tokenId, 4=새 tokenId, 5=유예(ms), 6=세션 키 접두사
     */
    private static final RedisScript<List> ROTATE_SCRIPT = new DefaultRedisScript<>("""
            local time = redis.call('TIME')
            local now = tonumber(time[1]) * 1000 + math.floor(tonumber(time[2]) / 1000)
            if redis.call('EXISTS', KEYS[1]) == 1 then
                redis.call('DEL', KEYS[1])
                redis.call('SREM', KEYS[2], ARGV[3])
                redis.call('SET', KEYS[3], ARGV[1], 'PX', ARGV[2])
                redis.call('SADD', KEYS[2], ARGV[4])
                redis.call('PEXPIRE', KEYS[2], ARGV[2])
                redis.call('SET', KEYS[4], ARGV[4], 'PX', ARGV[2])
                redis.call('ZADD', KEYS[5], now, ARGV[3])
                redis.call('ZREMRANGEBYSCORE', KEYS[5], '-inf', now - tonumber(ARGV[5]))
                redis.call('PEXPIRE', KEYS[5], ARGV[5])
                return {'ROTATED', ARGV[4]}
            end
            local current = redis.call('GET', KEYS[4])
            if not current or redis.call('EXISTS', ARGV[6] .. current) == 0 then
                return {'NOT_FOUND', ''}
            end
            local retiredAt = redis.call('ZSCORE', KEYS[5], ARGV[3])
            if retiredAt and now - tonumber(retiredAt) <= tonumber(ARGV[5]) then
                return {'GRACE_RETRY', current}
            end
            redis.call('DEL', ARGV[6] .. current)
            redis.call('SREM', KEYS[2], current)
            redis.call('DEL', KEYS[4], KEYS[5])
            return {'REUSED', ''}
            """, List.class);

    /**
     * 로그아웃: 제시된 토큰이 이미 교체된 예전 토큰이어도, 그 체인의 현재 세션까지 지운다.
     * KEYS: 1=체인, 2=세션 목록, 3=최근 교체 목록, 4=제시된 세션 / ARGV: 1=제시된 tokenId, 2=세션 키 접두사
     */
    private static final RedisScript<Long> DELETE_CHAIN_SCRIPT = new DefaultRedisScript<>("""
            local current = redis.call('GET', KEYS[1])
            if current then
                redis.call('DEL', ARGV[2] .. current)
                redis.call('SREM', KEYS[2], current)
            end
            redis.call('DEL', KEYS[4], KEYS[1], KEYS[3])
            redis.call('SREM', KEYS[2], ARGV[1])
            return 1
            """, Long.class);

    /**
     * 회원의 모든 세션과 체인 기록을 지운다. 체인 기록까지 지워야, 이후 옛 토큰이 와도 다른 세션에 영향 없이 거절된다.
     * KEYS: 1=세션 목록 / ARGV: 1=세션 키 접두사, 2=체인 키 접두사, 3=최근 교체 목록 키 접두사
     */
    private static final RedisScript<Long> DELETE_ALL_SCRIPT = new DefaultRedisScript<>("""
            local tokenIds = redis.call('SMEMBERS', KEYS[1])
            for _, tokenId in ipairs(tokenIds) do
                redis.call('DEL', ARGV[1] .. tokenId)
                local separator = string.find(tokenId, '.', 1, true)
                local chainId = separator and string.sub(tokenId, 1, separator - 1) or tokenId
                redis.call('DEL', ARGV[2] .. chainId, ARGV[3] .. chainId)
            end
            redis.call('DEL', KEYS[1])
            return #tokenIds
            """, Long.class);

    public enum RotationStatus { ROTATED, GRACE_RETRY, REUSED, NOT_FOUND }

    public record RotationResult(RotationStatus status, String tokenId) {
    }

    private final StringRedisTemplate redisTemplate;
    private final JwtProperties jwtProperties;

    /** 새 로그인용 tokenId (새 체인 시작). */
    public static String newChainTokenId() {
        return nextTokenId(UUID.randomUUID().toString());
    }

    /** 같은 체인 안의 다음 tokenId. */
    public static String nextTokenId(String chainId) {
        return chainId + CHAIN_SEPARATOR + UUID.randomUUID();
    }

    /** tokenId가 속한 체인. 체인 도입 전에 발급된 토큰(구분자 없음)은 tokenId 자체를 체인으로 본다. */
    public static String chainOf(String tokenId) {
        int separator = tokenId.indexOf(CHAIN_SEPARATOR);
        return separator < 0 ? tokenId : tokenId.substring(0, separator);
    }

    public void save(Long memberId, String tokenId) {
        redisTemplate.execute(
                SAVE_SCRIPT,
                List.of(key(memberId, tokenId), sessionsKey(memberId), chainKey(memberId, chainOf(tokenId))),
                VALID_MARKER, String.valueOf(jwtProperties.refreshExpiration()), tokenId
        );
    }

    @SuppressWarnings("unchecked")
    public RotationResult rotate(Long memberId, String tokenId, Duration gracePeriod) {
        String chainId = chainOf(tokenId);
        String newTokenId = nextTokenId(chainId);
        List<Object> result = redisTemplate.execute(
                ROTATE_SCRIPT,
                List.of(key(memberId, tokenId), sessionsKey(memberId), key(memberId, newTokenId),
                        chainKey(memberId, chainId), retiredKey(memberId, chainId)),
                VALID_MARKER,
                String.valueOf(jwtProperties.refreshExpiration()),
                tokenId,
                newTokenId,
                String.valueOf(gracePeriod.toMillis()),
                sessionKeyPrefix(memberId)
        );
        if (result == null || result.size() < 2) {
            // 스크립트/직렬화 이상을 "세션 없음"으로 처리하면 장애가 일반 인증 실패(AUTH_005)로 가려진다
            throw new IllegalStateException("refresh token 교체 스크립트 결과가 올바르지 않습니다: " + result);
        }
        return new RotationResult(RotationStatus.valueOf(String.valueOf(result.get(0))), String.valueOf(result.get(1)));
    }

    public void deleteOne(Long memberId, String tokenId) {
        String chainId = chainOf(tokenId);
        redisTemplate.execute(
                DELETE_CHAIN_SCRIPT,
                List.of(chainKey(memberId, chainId), sessionsKey(memberId), retiredKey(memberId, chainId), key(memberId, tokenId)),
                tokenId, sessionKeyPrefix(memberId)
        );
    }

    public void deleteAllByMemberId(Long memberId) {
        redisTemplate.execute(
                DELETE_ALL_SCRIPT,
                List.of(sessionsKey(memberId)),
                sessionKeyPrefix(memberId), CHAIN_KEY_PREFIX + memberId + ":", RETIRED_KEY_PREFIX + memberId + ":"
        );
    }

    private String key(Long memberId, String tokenId) {
        return sessionKeyPrefix(memberId) + tokenId;
    }

    /** Lua 스크립트 안에서 세션 키를 만들 때도 같은 접두사를 쓰도록 한 곳에서 정의한다. */
    private String sessionKeyPrefix(Long memberId) {
        return KEY_PREFIX + memberId + ":";
    }

    private String sessionsKey(Long memberId) {
        return SESSIONS_KEY_PREFIX + memberId;
    }

    private String chainKey(Long memberId, String chainId) {
        return CHAIN_KEY_PREFIX + memberId + ":" + chainId;
    }

    private String retiredKey(Long memberId, String chainId) {
        return RETIRED_KEY_PREFIX + memberId + ":" + chainId;
    }
}
