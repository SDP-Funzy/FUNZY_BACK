package com.sdp1617.backend.auth.service;

import com.sdp1617.backend.auth.dto.TokenResponse;
import com.sdp1617.backend.auth.jwt.JwtClaims;
import com.sdp1617.backend.auth.jwt.JwtProvider;
import com.sdp1617.backend.auth.jwt.TokenType;
import com.sdp1617.backend.auth.repository.MemberRepository;
import com.sdp1617.backend.auth.repository.MemberTokenState;
import com.sdp1617.backend.auth.repository.RefreshTokenRepository;
import com.sdp1617.backend.auth.repository.RefreshTokenRepository.RotationResult;
import com.sdp1617.backend.global.error.CustomException;
import com.sdp1617.backend.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class TokenService {

    /**
     * 교체 직후 예전 토큰을 받아주는 시간. 앱이 access 만료 직후 여러 요청에서 동시에 재발급을 부르면
     * 두 번째 요청은 이미 교체된 토큰을 보내게 되는데, 이걸 탈취로 처리하면 정상 사용자가 로그아웃된다.
     */
    static final Duration ROTATION_GRACE_PERIOD = Duration.ofSeconds(30);

    private final JwtProvider jwtProvider;
    private final RefreshTokenRepository refreshTokenRepository;
    private final MemberRepository memberRepository;

    /**
     * 로그인: 새 로그인 세션을 시작한다.
     * @param sessionVersion 로그인을 확인할 때 회원과 함께 읽은 세션 버전 (#124)
     */
    public TokenResponse issueTokens(Long memberId, int sessionVersion) {
        String tokenId = RefreshTokenRepository.newChainTokenId();
        String accessToken = jwtProvider.createAccessToken(memberId, sessionVersion);
        String refreshToken = jwtProvider.createRefreshToken(memberId, tokenId, sessionVersion);
        refreshTokenRepository.save(memberId, tokenId);
        return new TokenResponse(accessToken, refreshToken);
    }

    /**
     * refresh token rotation: 재발급할 때마다 refresh token도 새로 발급하고 기존 토큰은 폐기한다.
     * 만료가 "마지막 사용 + 30일"로 밀려, 앱을 쓰는 동안은 로그인이 유지되고 30일 동안 안 쓰면 로그아웃된다.
     * 교체된 예전 토큰이 다시 오면(유예 대상 제외) 탈취로 보고 그 로그인 체인만 폐기한다 — 다른 기기는 유지.
     * 체인 단위 판단 규칙은 {@link RefreshTokenRepository} 참고.
     * 새 토큰은 로그인할 때의 세션 버전을 이어받고, 비밀번호 변경 등으로 끊긴 세션이면 재발급하지 않는다 (#124).
     */
    public TokenResponse reissue(String refreshToken) {
        JwtClaims claims = jwtProvider.parse(refreshToken, TokenType.REFRESH);
        // refresh token은 세션을 끊은 커밋 뒤에 지워지므로, 그 사이에 재발급을 시도한 토큰도 여기서 막는다.
        // 탈퇴한 회원은 access token 필터와 같이 AUTH_003
        MemberTokenState state = memberRepository.findActiveTokenStateById(claims.memberId())
                .orElseThrow(() -> new CustomException(ErrorCode.AUTH_003));
        if (state.isRevoked(claims.sessionVersion())) {
            throw new CustomException(ErrorCode.AUTH_027);
        }

        RotationResult result = refreshTokenRepository.rotate(claims.memberId(), claims.tokenId(), ROTATION_GRACE_PERIOD);

        return switch (result.status()) {
            case ROTATED, GRACE_RETRY -> new TokenResponse(
                    jwtProvider.createAccessToken(claims.memberId(), claims.sessionVersion()),
                    jwtProvider.createRefreshToken(claims.memberId(), result.tokenId(), claims.sessionVersion()));
            // REUSED는 저장소에서 이미 그 체인을 폐기했다
            case REUSED, NOT_FOUND -> throw new CustomException(ErrorCode.AUTH_005);
        };
    }

    public void revokeAllSessions(Long memberId) {
        refreshTokenRepository.deleteAllByMemberId(memberId);
    }

    public void revokeSession(Long memberId, String refreshToken) {
        JwtClaims claims = jwtProvider.parse(refreshToken, TokenType.REFRESH);

        if (!claims.memberId().equals(memberId)) {
            throw new CustomException(ErrorCode.AUTH_003);
        }

        refreshTokenRepository.deleteOne(memberId, claims.tokenId());
    }
}
