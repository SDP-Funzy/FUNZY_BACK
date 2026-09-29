package com.sdp1617.backend.auth.migration;

import com.sdp1617.backend.auth.entity.AuthProvider;
import com.sdp1617.backend.auth.entity.Member;
import com.sdp1617.backend.auth.entity.SocialConnection;
import com.sdp1617.backend.auth.repository.MemberRepository;
import com.sdp1617.backend.auth.repository.SocialConnectionRepository;
import com.sdp1617.backend.global.error.ConstraintViolations;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * SocialConnection(다중 소셜 로그인 수단) 도입 전까지 Member.provider/providerId 하나로만
 * 표현되던 기존 소셜 회원들을 SocialConnection 테이블로 1회성 백필한다.
 * Flyway/Liquibase 없이 ddl-auto: update를 쓰는 프로젝트라 앱 시작 시점에 실행되는
 * ApplicationRunner로 처리 — 이미 연결이 있는 회원은 건너뛰므로 매 시작마다 재실행돼도 안전(멱등).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SocialConnectionBackfiller implements ApplicationRunner {

    private final MemberRepository memberRepository;
    private final SocialConnectionRepository socialConnectionRepository;
    private final TransactionTemplate transactionTemplate;

    @Override
    public void run(ApplicationArguments args) {
        List<Member> socialMembers = memberRepository.findByProviderNot(AuthProvider.LOCAL);
        int backfilled = 0;
        int failed = 0;
        for (Member member : socialMembers) {
            switch (backfillOne(member)) {
                case BACKFILLED -> backfilled++;
                case FAILED -> failed++;
                case SKIPPED -> { }
            }
        }
        if (backfilled > 0) {
            log.info("SocialConnection 백필 완료: {}건", backfilled);
        }
        if (failed > 0) {
            log.error("SocialConnection 백필 중 {}건 실패 — 데이터 확인 필요 (위 로그의 memberId 참고)", failed);
        }
    }

    /**
     * 회원 한 명씩 별도의 짧은 트랜잭션으로 처리한다. 배포 중 잠깐 겹쳐서 여러 인스턴스가
     * 동시에 백필을 실행하더라도, 한 건이 uk_social_connection_provider_provider_id 위반으로
     * 실패해도(다른 인스턴스가 먼저 같은 연결을 만든 경우) 그 건만 건너뛰고 앱 부팅은 막지 않으며,
     * 이미 처리된 나머지 회원들의 커밋에도 영향을 주지 않는다(전체 배치 롤백 방지).
     * 단, uk_social_connection_provider_provider_id 위반이 아닌 다른 무결성 위반(예: 과거 데이터
     * 손상으로 providerId가 비어있는 경우의 NOT NULL 위반)까지 동시성 충돌로 오인해 조용히
     * 삼키면 안 되므로, 해당 제약 이름일 때만 SKIPPED로 처리하고 그 외에는 FAILED로 크게 로그를
     * 남긴다 — 단, 이 실패 때문에 나머지 회원 백필이나 앱 부팅까지 막지는 않는다(격리 유지).
     */
    private BackfillResult backfillOne(Member member) {
        try {
            boolean created = Boolean.TRUE.equals(transactionTemplate.execute(status -> {
                if (socialConnectionRepository.existsByMember_IdAndProvider(member.getId(), member.getProvider())) {
                    return false;
                }
                socialConnectionRepository.saveAndFlush(
                        SocialConnection.create(member, member.getProvider(), member.getProviderId()));
                return true;
            }));
            return created ? BackfillResult.BACKFILLED : BackfillResult.SKIPPED;
        } catch (DataIntegrityViolationException exception) {
            if (violatesUniqueConnectionConstraint(exception)) {
                log.debug("SocialConnection 백필 중 유니크 제약 충돌(다른 인스턴스가 이미 연결한 것으로 간주): memberId={}",
                        member.getId(), exception);
                return BackfillResult.SKIPPED;
            }
            log.error("SocialConnection 백필 실패(데이터 확인 필요): memberId={}", member.getId(), exception);
            return BackfillResult.FAILED;
        }
    }

    private boolean violatesUniqueConnectionConstraint(DataIntegrityViolationException exception) {
        return ConstraintViolations.nameOf(exception)
                .filter("uk_social_connection_provider_provider_id"::equalsIgnoreCase)
                .isPresent();
    }

    private enum BackfillResult {
        BACKFILLED, SKIPPED, FAILED
    }
}
