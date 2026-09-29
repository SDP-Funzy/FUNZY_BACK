package com.sdp1617.backend.auth.repository;

import com.sdp1617.backend.auth.entity.AuthProvider;
import com.sdp1617.backend.auth.entity.Member;
import com.sdp1617.backend.global.error.ConstraintViolations;
import com.sdp1617.backend.global.error.CustomException;
import com.sdp1617.backend.global.error.ErrorCode;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemberRepository extends JpaRepository<Member, Long> {

    boolean existsByEmail(String email);

    boolean existsByNickname(String nickname);

    Optional<Member> findByEmail(String email);

    List<Member> findByProviderNot(AuthProvider provider);

    Optional<Member> findByFollowCode(String followCode);

    /**
     * 같은 회원에 대한 소셜 연결 해제 요청 두 개가 동시에 들어오면(예: KAKAO/GOOGLE 동시 해제),
     * 각자 상대방의 삭제를 못 본 채로 "마지막 수단 아님"을 통과해 회원이 비밀번호도 소셜 연결도
     * 전부 없는 상태(완전 잠김)가 될 수 있다. 비관적 쓰기 락으로 회원 행을 잠가 이 요청들을
     * 직렬화한다 — 먼저 잠근 트랜잭션이 커밋될 때까지 다음 트랜잭션은 대기했다가, 커밋 후의
     * 최신 연결 개수를 보고 판단하게 된다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Member m where m.id = :id")
    Optional<Member> findByIdForUpdate(@Param("id") Long id);

    @Modifying
    @Query("update Member m set m.failedLoginCount = m.failedLoginCount + 1 where m.id = :id")
    int incrementFailedLoginCount(@Param("id") Long id);

    /**
     * existsByNickname 체크 후 저장하는 방식은 동시 요청 사이에 경쟁 상태(race condition)가 있어
     * 최종 방어선인 DB 유니크 제약 위반으로 실패할 수 있다. 그 경우를 그 자리에서 즉시 잡아
     * (saveAndFlush로 커밋을 기다리지 않고 바로 제약 위반을 드러냄) AUTH_007로 매핑한다.
     * Member는 email/provider+provider_id 유니크 제약도 함께 가지고 있으므로, 실제로 위반된
     * 제약이 닉네임(uk_member_nickname)인 경우에만 AUTH_007로 바꾸고 그 외에는 원래 예외를
     * 그대로 던져 GlobalExceptionHandler의 COMMON_005 처리로 흘려보낸다.
     */
    default Member saveWithNicknameUniqueness(Member member) {
        try {
            return saveAndFlush(member);
        } catch (DataIntegrityViolationException exception) {
            if (violatesNicknameConstraint(exception)) {
                throw new CustomException(ErrorCode.AUTH_007);
            }
            throw exception;
        }
    }

    private boolean violatesNicknameConstraint(DataIntegrityViolationException exception) {
        return ConstraintViolations.nameOf(exception)
                .filter("uk_member_nickname"::equalsIgnoreCase)
                .isPresent();
    }
}
