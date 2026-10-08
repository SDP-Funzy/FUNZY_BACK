package com.sdp1617.backend.auth.repository;

/** 요청마다 토큰을 검증할 때 쓰는 회원 상태 (#124). 탈퇴한 회원은 조회되지 않는다. */
public record MemberTokenState(
        Integer sessionVersion
) {

    /** 토큰의 세션 버전이 회원의 현재 버전보다 낮으면(그 뒤에 세션을 끊었으면) true. */
    public boolean isRevoked(int tokenSessionVersion) {
        int current = sessionVersion == null ? 0 : sessionVersion;
        return tokenSessionVersion < current;
    }
}
