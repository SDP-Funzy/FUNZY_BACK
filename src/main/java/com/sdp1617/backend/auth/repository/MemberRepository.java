package com.sdp1617.backend.auth.repository;

import com.sdp1617.backend.auth.entity.Member;
import com.sdp1617.backend.global.error.ConstraintViolations;
import com.sdp1617.backend.global.error.CustomException;
import com.sdp1617.backend.global.error.ErrorCode;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemberRepository extends JpaRepository<Member, Long> {

    boolean existsByEmail(String email);

    boolean existsByNickname(String nickname);

    /** 닉네임을 새로 쓸 수 없는지: 이미 사용 중이거나, 탈퇴 회원용으로 예약된 닉네임. */
    default boolean isNicknameTaken(String nickname) {
        return Member.isReservedNickname(nickname) || existsByNickname(nickname);
    }

    Optional<Member> findByEmail(String email);

    Optional<Member> findByNickname(String nickname);


    /** 닉네임 앞부분이 일치하는 친구(맞팔). 나와 탈퇴한 회원은 제외. prefix는 소문자로 바꾸고 LIKE 와일드카드를 이스케이프한 값. */
    @Query("""
            select m from Member m
            where lower(m.nickname) like :prefix escape '\\'
              and m.id <> :viewerId
              and m.withdrawnAt is null
              and exists (select 1 from FollowRelation f
                          where (f.memberIdA = :viewerId and f.memberIdB = m.id)
                             or (f.memberIdA = m.id and f.memberIdB = :viewerId))
            order by m.nickname asc, m.id asc
            """)
    List<Member> searchFriendsByNicknamePrefix(
            @Param("viewerId") Long viewerId, @Param("prefix") String prefix, Pageable pageable);

    /** 닉네임 앞부분이 일치하는, 친구가 아닌 회원. 나와 탈퇴한 회원은 제외. */
    @Query("""
            select m from Member m
            where lower(m.nickname) like :prefix escape '\\'
              and m.id <> :viewerId
              and m.withdrawnAt is null
              and not exists (select 1 from FollowRelation f
                              where (f.memberIdA = :viewerId and f.memberIdB = m.id)
                                 or (f.memberIdA = m.id and f.memberIdB = :viewerId))
            order by m.nickname asc, m.id asc
            """)
    List<Member> searchNonFriendsByNicknamePrefix(
            @Param("viewerId") Long viewerId, @Param("prefix") String prefix, Pageable pageable);

    /** 탈퇴하지 않은 회원. 탈퇴해도 회원 행은 남으므로(익명화) 회원 정보를 읽거나 바꿀 때는 findById 대신 이걸 쓴다. */
    default Optional<Member> findActiveById(Long id) {
        return findById(id).filter(member -> !member.isWithdrawn());
    }

    /**
     * 회원 정보를 바꿀 때 쓴다. 행을 잠가 탈퇴({@code withdraw})와 직렬화한 뒤 탈퇴 여부를 확인한다.
     * 잠그지 않고 읽으면, 진행 중인 탈퇴가 커밋된 뒤에 이 수정이 적용돼 탈퇴한 회원에 사진·닉네임 등이 다시 붙는다.
     */
    default Optional<Member> findActiveByIdForUpdate(Long id) {
        return findByIdForUpdate(id).filter(member -> !member.isWithdrawn());
    }

    /** 탈퇴하지 않은 회원인지. 탈퇴해도 회원 행은 남으므로(익명화) existsById 대신 이걸 쓴다. */
    @Query("select count(m) > 0 from Member m where m.id = :id and m.withdrawnAt is null")
    boolean existsActiveById(@Param("id") Long id);

    /**
     * 세션 버전을 DB에서 1 올린다 (#124). Java에서 "읽은 값 + 1"로 쓰면, 행을 잠그지 않는 잠금 해제와 비밀번호 변경이
     * 겹칠 때 둘 다 같은 값을 써서 한 번의 증가가 사라진다. 영속 상태의 Member 값은 갱신되지 않지만,
     * Member는 @DynamicUpdate라 같은 트랜잭션에서 다른 칸(비밀번호 등)을 저장해도 이 값을 덮어쓰지 않는다.
     */
    @Modifying(flushAutomatically = true)
    @Query("update Member m set m.sessionVersion = coalesce(m.sessionVersion, 0) + 1 where m.id = :id")
    int incrementSessionVersion(@Param("id") Long id);

    /** 안 쓰는 사진 정리용 (#122). 주어진 key 중 프로필 사진으로 쓰고 있는 것. */
    @Query("select m.profileImageKey from Member m where m.profileImageKey in :keys")
    List<String> findProfileImageKeysIn(@Param("keys") Collection<String> keys);

    boolean existsByProfileImageKeyIsNotNull();

    /** 토큰 검증용. 탈퇴하지 않은 회원이면 세션 버전을 함께 돌려준다 (요청마다 조회 1번, #124). */
    @Query("select new com.sdp1617.backend.auth.repository.MemberTokenState(m.sessionVersion) "
            + "from Member m where m.id = :id and m.withdrawnAt is null")
    Optional<MemberTokenState> findActiveTokenStateById(@Param("id") Long id);

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
