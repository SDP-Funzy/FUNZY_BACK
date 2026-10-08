package com.sdp1617.backend.auth.jwt;

/**
 * @param sessionVersion 로그인할 때 읽은 회원의 세션 버전. 재발급해도 바뀌지 않는다.
 *                       비밀번호 변경 등으로 회원의 세션 버전이 올라가면 이전 버전의 토큰을 모두 거절한다 (#124).
 */
public record JwtClaims(
        Long memberId,
        String tokenId,
        int sessionVersion
) {
}
