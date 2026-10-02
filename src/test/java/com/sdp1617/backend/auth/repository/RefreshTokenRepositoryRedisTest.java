package com.sdp1617.backend.auth.repository;

import com.sdp1617.backend.auth.jwt.JwtProperties;
import com.sdp1617.backend.auth.repository.RefreshTokenRepository.RotationResult;
import com.sdp1617.backend.auth.repository.RefreshTokenRepository.RotationStatus;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * refresh token 체인 교체 로직은 Lua 스크립트 안에서 동작하므로 실제 Redis로 검증한다.
 * REDIS_HOST/REDIS_PORT(기본 localhost:6379)에 Redis가 없으면 로컬에서는 건너뛰지만, CI(CI=true)에서는 실패시킨다 —
 * CI에서 조용히 건너뛰면 깨진 스크립트가 초록불로 머지·배포될 수 있다.
 */
class RefreshTokenRepositoryRedisTest {

    /** "유예 안" 판정용. 느린 CI에서도 시간 초과로 흔들리지 않게 넉넉히 둔다. */
    private static final Duration GRACE = Duration.ofSeconds(10);
    /** "유예 지남" 판정용. 충분히 기다린 뒤에만 판정하므로 짧아도 흔들리지 않는다. */
    private static final Duration SHORT_GRACE = Duration.ofMillis(200);
    private static final long AFTER_SHORT_GRACE_MILLIS = SHORT_GRACE.toMillis() + 400;

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redisTemplate;

    private RefreshTokenRepository repository;
    private Long memberId;

    @BeforeAll
    static void connect() {
        String host = System.getenv().getOrDefault("REDIS_HOST", "localhost");
        int port = Integer.parseInt(System.getenv().getOrDefault("REDIS_PORT", "6379"));
        LettuceClientConfiguration clientConfiguration = LettuceClientConfiguration.builder()
                .commandTimeout(Duration.ofSeconds(2))
                .clientOptions(ClientOptions.builder()
                        .socketOptions(SocketOptions.builder().connectTimeout(Duration.ofSeconds(1)).build())
                        .build())
                .build();
        connectionFactory = new LettuceConnectionFactory(new RedisStandaloneConfiguration(host, port), clientConfiguration);
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();

        boolean available;
        try (RedisConnection connection = connectionFactory.getConnection()) {
            available = "PONG".equals(connection.ping());
        } catch (RuntimeException exception) {
            available = false;
        }
        String reason = "Redis(" + host + ":" + port + ")에 연결할 수 없음";
        if ("true".equalsIgnoreCase(System.getenv("CI"))) {
            assertTrue(available, reason + " — CI에서는 필수");
        }
        assumeTrue(available, reason + " — 로컬이라 건너뜀");
        redisTemplate = new StringRedisTemplate(connectionFactory);
    }

    @AfterAll
    static void disconnect() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @BeforeEach
    void setUp() {
        repository = new RefreshTokenRepository(redisTemplate, new JwtProperties("unused", 3_600_000L, 2_592_000_000L));
        // 다른 테스트/로컬 데이터와 겹치지 않는 회원 ID
        memberId = 9_000_000_000L + ThreadLocalRandom.current().nextLong(1_000_000_000L);
    }

    @AfterEach
    void tearDown() {
        Set<String> keys = redisTemplate.keys("refresh-token*:" + memberId + "*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    private String login() {
        String tokenId = RefreshTokenRepository.newChainTokenId();
        repository.save(memberId, tokenId);
        return tokenId;
    }

    private boolean alive(String tokenId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey("refresh-token:" + memberId + ":" + tokenId));
    }

    @Test
    void 살아있는_토큰은_같은_체인의_새_토큰으로_교체되고_예전_세션은_폐기된다() {
        String t1 = login();

        RotationResult result = repository.rotate(memberId, t1, GRACE);

        assertEquals(RotationStatus.ROTATED, result.status());
        assertNotEquals(t1, result.tokenId());
        assertEquals(RefreshTokenRepository.chainOf(t1), RefreshTokenRepository.chainOf(result.tokenId()));
        assertFalse(alive(t1));
        assertTrue(alive(result.tokenId()));
    }

    @Test
    void 유예시간_안에_방금_교체된_토큰이_오면_현재_토큰을_다시_내준다() {
        String t1 = login();
        String t2 = repository.rotate(memberId, t1, GRACE).tokenId();

        RotationResult retry = repository.rotate(memberId, t1, GRACE);

        assertEquals(RotationStatus.GRACE_RETRY, retry.status());
        assertEquals(t2, retry.tokenId());
        assertTrue(alive(t2));
    }

    @Test
    void 유예시간_안에_두_번_교체돼도_늦게_온_가장_예전_토큰은_현재_토큰을_받는다() {
        String t1 = login();
        String t2 = repository.rotate(memberId, t1, GRACE).tokenId();
        String t3 = repository.rotate(memberId, t2, GRACE).tokenId();

        RotationResult retry = repository.rotate(memberId, t1, GRACE);

        assertEquals(RotationStatus.GRACE_RETRY, retry.status());
        assertEquals(t3, retry.tokenId());
    }

    @Test
    void 유예시간이_지난_예전_토큰이_오면_그_체인만_폐기하고_다른_기기는_유지한다() throws InterruptedException {
        String phone = login();
        String tablet = login();
        String phoneCurrent = repository.rotate(memberId, phone, SHORT_GRACE).tokenId();
        Thread.sleep(AFTER_SHORT_GRACE_MILLIS);

        RotationResult reuse = repository.rotate(memberId, phone, SHORT_GRACE);

        assertEquals(RotationStatus.REUSED, reuse.status());
        assertFalse(alive(phoneCurrent));
        assertTrue(alive(tablet));
        // 폐기된 체인의 어떤 토큰으로도 더는 재발급되지 않는다
        assertEquals(RotationStatus.NOT_FOUND, repository.rotate(memberId, phoneCurrent, GRACE).status());
    }

    @Test
    void 체인이_방금_교체됐어도_오래전에_교체된_토큰은_유예를_받지_못한다() throws InterruptedException {
        String t1 = login();
        String t2 = repository.rotate(memberId, t1, SHORT_GRACE).tokenId();
        Thread.sleep(AFTER_SHORT_GRACE_MILLIS);
        String t3 = repository.rotate(memberId, t2, SHORT_GRACE).tokenId();

        // 훔쳐둔 옛 토큰(t1)을 정상 교체 직후에 끼워 넣어도 현재 토큰(t3)을 받지 못한다
        RotationResult result = repository.rotate(memberId, t1, SHORT_GRACE);

        assertEquals(RotationStatus.REUSED, result.status());
        assertFalse(alive(t3));
    }

    @Test
    void 이미_교체된_토큰으로_로그아웃해도_그_체인의_현재_세션이_지워진다() {
        String t1 = login();
        String t2 = repository.rotate(memberId, t1, GRACE).tokenId();

        repository.deleteOne(memberId, t1);

        assertFalse(alive(t2));
        assertEquals(RotationStatus.NOT_FOUND, repository.rotate(memberId, t2, GRACE).status());
    }

    @Test
    void 전체_폐기_후에는_옛_토큰이_와도_새로_로그인한_세션에_영향이_없다() {
        String t1 = login();
        repository.rotate(memberId, t1, GRACE);
        repository.deleteAllByMemberId(memberId);
        String relogin = login();

        RotationResult result = repository.rotate(memberId, t1, GRACE);

        assertEquals(RotationStatus.NOT_FOUND, result.status());
        assertTrue(alive(relogin));
    }

    @Test
    void 체인_도입_전에_발급된_토큰도_교체된다() {
        String legacy = java.util.UUID.randomUUID().toString();
        repository.save(memberId, legacy);

        RotationResult result = repository.rotate(memberId, legacy, GRACE);

        assertEquals(RotationStatus.ROTATED, result.status());
        assertEquals(legacy, RefreshTokenRepository.chainOf(result.tokenId()));
        assertTrue(alive(result.tokenId()));
    }
}
