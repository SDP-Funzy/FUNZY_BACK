package com.sdp1617.backend.auth.service;

import com.sdp1617.backend.auth.dto.TokenResponse;
import com.sdp1617.backend.auth.jwt.JwtClaims;
import com.sdp1617.backend.auth.jwt.JwtProvider;
import com.sdp1617.backend.auth.jwt.TokenType;
import com.sdp1617.backend.auth.repository.RefreshTokenRepository;
import com.sdp1617.backend.auth.repository.RefreshTokenRepository.RotationResult;
import com.sdp1617.backend.auth.repository.RefreshTokenRepository.RotationStatus;
import com.sdp1617.backend.global.error.CustomException;
import com.sdp1617.backend.global.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TokenServiceTest {

    @Mock
    private JwtProvider jwtProvider;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @InjectMocks
    private TokenService tokenService;

    @Test
    void issueTokens는_accessToken과_refreshToken을_발급하고_refreshToken을_저장한다() {
        when(jwtProvider.createAccessToken(1L)).thenReturn("access-token");
        when(jwtProvider.createRefreshToken(org.mockito.ArgumentMatchers.eq(1L), any())).thenReturn("refresh-token");

        TokenResponse response = tokenService.issueTokens(1L);

        assertEquals("access-token", response.accessToken());
        assertEquals("refresh-token", response.refreshToken());
        // 로그인마다 새 체인으로 시작한다 (tokenId = 체인ID.고유값)
        ArgumentCaptor<String> tokenId = ArgumentCaptor.forClass(String.class);
        verify(refreshTokenRepository).save(eq(1L), tokenId.capture());
        assertTrue(tokenId.getValue().contains("."));
        assertEquals(RefreshTokenRepository.chainOf(tokenId.getValue()), tokenId.getValue().split("\\.")[0]);
    }

    private void givenRotation(RotationStatus status, String tokenId) {
        when(jwtProvider.parse("refresh-token", TokenType.REFRESH)).thenReturn(new JwtClaims(1L, "session-1"));
        when(refreshTokenRepository.rotate(1L, "session-1", TokenService.ROTATION_GRACE_PERIOD))
                .thenReturn(new RotationResult(status, tokenId));
    }

    @Test
    void 세션이_살아있으면_기존_세션을_교체하고_새_accessToken과_새_refreshToken을_발급한다() {
        givenRotation(RotationStatus.ROTATED, "session-2");
        when(jwtProvider.createAccessToken(1L)).thenReturn("new-access-token");
        when(jwtProvider.createRefreshToken(1L, "session-2")).thenReturn("new-refresh-token");

        TokenResponse response = tokenService.reissue("refresh-token");

        assertEquals("new-access-token", response.accessToken());
        assertEquals("new-refresh-token", response.refreshToken());
    }

    @Test
    void 동시_재발급으로_유예시간_안에_예전_토큰이_오면_같은_새_세션의_토큰을_발급한다() {
        givenRotation(RotationStatus.GRACE_RETRY, "session-2");
        when(jwtProvider.createAccessToken(1L)).thenReturn("new-access-token");
        when(jwtProvider.createRefreshToken(1L, "session-2")).thenReturn("new-refresh-token");

        TokenResponse response = tokenService.reissue("refresh-token");

        assertEquals("new-refresh-token", response.refreshToken());
        verify(refreshTokenRepository, never()).deleteAllByMemberId(any());
    }

    @Test
    void 교체된_토큰이_재사용되면_AUTH_005를_던지고_다른_기기_세션은_건드리지_않는다() {
        givenRotation(RotationStatus.REUSED, null);

        CustomException exception = assertThrows(CustomException.class, () -> tokenService.reissue("refresh-token"));

        assertEquals(ErrorCode.AUTH_005, exception.getErrorCode());
        // 체인 폐기는 저장소(rotate)가 처리한다. 회원 전체 세션을 지우면 다른 기기까지 로그아웃된다.
        verify(refreshTokenRepository, never()).deleteAllByMemberId(any());
        verify(jwtProvider, never()).createAccessToken(any());
    }

    @Test
    void 저장된_세션도_교체_기록도_없으면_AUTH_005_예외를_던진다() {
        givenRotation(RotationStatus.NOT_FOUND, null);

        CustomException exception = assertThrows(CustomException.class, () -> tokenService.reissue("refresh-token"));

        assertEquals(ErrorCode.AUTH_005, exception.getErrorCode());
        verify(refreshTokenRepository, never()).deleteAllByMemberId(any());
    }

    @Test
    void revokeSession은_해당_세션만_삭제한다() {
        String refreshToken = "refresh-token";
        when(jwtProvider.parse(refreshToken, TokenType.REFRESH)).thenReturn(new JwtClaims(1L, "session-1"));

        tokenService.revokeSession(1L, refreshToken);

        verify(refreshTokenRepository).deleteOne(1L, "session-1");
    }

    @Test
    void revokeSession시_토큰의_memberId가_다르면_AUTH_003_예외를_던진다() {
        String refreshToken = "refresh-token";
        when(jwtProvider.parse(refreshToken, TokenType.REFRESH)).thenReturn(new JwtClaims(2L, "session-1"));

        CustomException exception = assertThrows(CustomException.class, () -> tokenService.revokeSession(1L, refreshToken));

        assertEquals(ErrorCode.AUTH_003, exception.getErrorCode());
    }
}
