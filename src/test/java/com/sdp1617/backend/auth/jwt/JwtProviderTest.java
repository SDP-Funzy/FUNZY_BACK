package com.sdp1617.backend.auth.jwt;

import com.sdp1617.backend.global.error.CustomException;
import com.sdp1617.backend.global.error.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtProviderTest {

    private final JwtProperties jwtProperties =
            new JwtProperties("test-jwt-secret-key-for-unit-test-1234567890", 3_600_000L, 2_592_000_000L);
    private final JwtProvider jwtProvider = new JwtProvider(jwtProperties);

    @Test
    void createAccessToken으로_만든_토큰에서_memberId를_복원한다() {
        String token = jwtProvider.createAccessToken(1L, 0);

        JwtClaims claims = jwtProvider.parse(token, TokenType.ACCESS);

        assertEquals(1L, claims.memberId());
        assertNull(claims.tokenId());
    }

    @Test
    void 세션_버전을_담고_복원한다() {
        assertEquals(3, jwtProvider.parse(jwtProvider.createAccessToken(1L, 3), TokenType.ACCESS).sessionVersion());
        assertEquals(3, jwtProvider.parse(
                jwtProvider.createRefreshToken(1L, "session-1", 3), TokenType.REFRESH).sessionVersion());
    }

    @Test
    void createRefreshToken으로_만든_토큰에서_memberId와_tokenId를_복원한다() {
        String token = jwtProvider.createRefreshToken(1L, "session-1", 0);

        JwtClaims claims = jwtProvider.parse(token, TokenType.REFRESH);

        assertEquals(1L, claims.memberId());
        assertEquals("session-1", claims.tokenId());
    }

    @Test
    void refreshToken을_ACCESS_타입으로_검증하면_AUTH_003_예외를_던진다() {
        String refreshToken = jwtProvider.createRefreshToken(1L, "session-1", 0);

        CustomException exception =
                assertThrows(CustomException.class, () -> jwtProvider.parse(refreshToken, TokenType.ACCESS));

        assertEquals(ErrorCode.AUTH_003, exception.getErrorCode());
    }

    @Test
    void accessToken을_REFRESH_타입으로_검증하면_AUTH_003_예외를_던진다() {
        String accessToken = jwtProvider.createAccessToken(1L, 0);

        CustomException exception =
                assertThrows(CustomException.class, () -> jwtProvider.parse(accessToken, TokenType.REFRESH));

        assertEquals(ErrorCode.AUTH_003, exception.getErrorCode());
    }

    @Test
    void 만료된_토큰은_AUTH_004_예외를_던진다() {
        JwtProvider expiredTokenProvider =
                new JwtProvider(new JwtProperties(jwtProperties.secret(), -1_000L, -1_000L));
        String expiredToken = expiredTokenProvider.createAccessToken(1L, 0);

        CustomException exception =
                assertThrows(CustomException.class, () -> jwtProvider.parse(expiredToken, TokenType.ACCESS));

        assertEquals(ErrorCode.AUTH_004, exception.getErrorCode());
    }

    @Test
    void 위조된_토큰은_AUTH_003_예외를_던진다() {
        CustomException exception = assertThrows(
                CustomException.class, () -> jwtProvider.parse("invalid.token.value", TokenType.ACCESS));

        assertEquals(ErrorCode.AUTH_003, exception.getErrorCode());
    }
}
