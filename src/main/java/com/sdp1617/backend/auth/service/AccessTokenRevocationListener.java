package com.sdp1617.backend.auth.service;

import com.sdp1617.backend.auth.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 전체 세션을 끊을 때(비밀번호 변경·재설정, 잠금 해제, 탈퇴) 회원의 세션 버전을 올려 이미 발급된 토큰도 무효로 만든다 (#124).
 * refresh token 저장소 정리({@link SessionRevocationListener})는 커밋 뒤에 하지만, 이 증가는 비밀번호 변경과
 * 같은 트랜잭션에서 해야 변경이 롤백되면 함께 취소된다.
 */
@Component
@RequiredArgsConstructor
public class AccessTokenRevocationListener {

    private final MemberRepository memberRepository;

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void handle(AllSessionsRevokedEvent event) {
        memberRepository.incrementSessionVersion(event.memberId());
    }
}
