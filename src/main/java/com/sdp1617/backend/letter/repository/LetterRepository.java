package com.sdp1617.backend.letter.repository;

import com.sdp1617.backend.letter.entity.Letter;
import com.sdp1617.backend.letter.entity.LetterStatus;
import java.util.Collection;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LetterRepository extends JpaRepository<Letter, Long> {

    /** 편지를 수정할 때 행을 잠가, 동시에 카드를 추가해 5장을 넘기는 등의 경쟁을 막는다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from Letter l where l.id = :id")
    Optional<Letter> findByIdForUpdate(@Param("id") Long id);

    /** 아직 보내지 않은(작성 중·완료) 내 편지 — 이어쓰기 목록. 최근 수정 순. */
    List<Letter> findBySender_IdAndStatusInOrderByUpdatedAtDescIdDesc(Long senderId, Collection<LetterStatus> statuses);

    /** 탈퇴 정리용. 편지를 잠가, 동시에 진행 중인 카드 추가가 끝난 뒤에 지운다 (안 잠그면 새 카드 FK 때문에 삭제가 실패). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from Letter l where l.sender.id = :senderId and l.status in :statuses")
    List<Letter> findBySenderIdAndStatusInForUpdate(
            @Param("senderId") Long senderId, @Param("statuses") Collection<LetterStatus> statuses);
}
