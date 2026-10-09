package com.sdp1617.backend.letter.repository;

import com.sdp1617.backend.letter.entity.LetterCard;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LetterCardRepository extends JpaRepository<LetterCard, Long> {

    /** 카드와 소속 편지를 함께 읽는다 (받는 사람 확인용). */
    @EntityGraph(attributePaths = "letter")
    Optional<LetterCard> findWithLetterById(Long id);

    /** 소속 편지 ID만 읽는다 — 편지를 잠그기 전에 엔티티를 미리 읽어 두면 잠금 후에도 예전 상태를 보게 되므로. */
    @Query("select x.letter.id from LetterCard x where x.id = :id")
    Optional<Long> findLetterIdById(@Param("id") Long id);

    /** 안 쓰는 사진 정리용 (#122). 주어진 key 중 카드가 쓰고 있는 것. */
    @Query("select x.imageKey from LetterCard x where x.imageKey in :keys")
    List<String> findImageKeysIn(@Param("keys") Collection<String> keys);

    boolean existsByImageKeyIsNotNull();

    /*
     * 마음카드 보관함 (#82): 보낸 편지(SENT)의 카드. 보낸함은 내가 보낸 편지, 받은함은 내가 받고 숨기지 않은 편지.
     * 값이 비어 있을 수 있는 파라미터는 cast로 타입을 지정한다 (PostgreSQL이 null 파라미터의 타입을 추론하지 못함, #126).
     */
    // 텍스트 블록은 줄 끝 공백을 지우므로, 앞뒤 쿼리와 붙지 않도록 공백을 포함한 한 줄 문자열로 둔다
    String BOX_CONDITION = " (l.status = com.sdp1617.backend.letter.entity.LetterStatus.SENT"
            + " and ((:asSender = true and s.id = :memberId)"
            + " or (:asSender = false and r.id = :memberId and l.recipientHiddenAt is null))) ";

    @Query("""
            select c from LetterCard c
            join fetch c.letter l
            join fetch l.sender s
            join fetch l.recipient r
            where """ + BOX_CONDITION + """
              and (cast(:startAt as LocalDateTime) is null or l.sentAt >= :startAt)
              and (cast(:endAt as LocalDateTime) is null or l.sentAt < :endAt)
              and (cast(:keyword as String) is null
                   or lower(c.content) like :keyword escape '\\'
                   or (:asSender = false and (lower(l.fromName) like :keyword escape '\\'
                                              or lower(s.nickname) like :keyword escape '\\'))
                   or (:asSender = true and (lower(l.toName) like :keyword escape '\\'
                                             or lower(r.nickname) like :keyword escape '\\')))
              and (cast(:cursorSentAt as LocalDateTime) is null
                   or l.sentAt < :cursorSentAt
                   or (l.sentAt = :cursorSentAt and c.id < :cursorId))
            order by l.sentAt desc, c.id desc
            """)
    List<LetterCard> findBoxCards(
            @Param("asSender") boolean asSender,
            @Param("memberId") Long memberId,
            @Param("startAt") LocalDateTime startAt,
            @Param("endAt") LocalDateTime endAt,
            @Param("keyword") String keyword,
            @Param("cursorSentAt") LocalDateTime cursorSentAt,
            @Param("cursorId") Long cursorId,
            Pageable pageable
    );

    /**
     * 상대방별 폴더. 한쪽(보낸함이면 보낸 사람, 받은함이면 받은 사람)은 항상 나라서, 양쪽 회원으로 묶으면 상대방별로 묶인다.
     * GROUP BY에 파라미터가 들어간 CASE 식을 쓰면 PostgreSQL이 SELECT의 같은 식과 같다고 보지 않아 오류가 나므로 쓰지 않는다 (#126).
     */
    @Query("""
            select (case when :asSender = true then r.id else s.id end) as memberId,
                   (case when :asSender = true then r.nickname else s.nickname end) as nickname,
                   count(c) as cardCount,
                   max(l.sentAt) as latestSentAt
            from LetterCard c
            join c.letter l
            join l.sender s
            join l.recipient r
            where """ + BOX_CONDITION + """
            group by s.id, s.nickname, r.id, r.nickname
            having (cast(:cursorSentAt as LocalDateTime) is null
                    or max(l.sentAt) < :cursorSentAt
                    or (max(l.sentAt) = :cursorSentAt
                        and case when :asSender = true then r.id else s.id end < :cursorId))
            order by max(l.sentAt) desc, case when :asSender = true then r.id else s.id end desc
            """)
    List<FolderSummaryProjection> findBoxFolders(
            @Param("asSender") boolean asSender,
            @Param("memberId") Long memberId,
            @Param("cursorSentAt") LocalDateTime cursorSentAt,
            @Param("cursorId") Long cursorId,
            Pageable pageable
    );

    @Query("""
            select c.imageUrl from LetterCard c
            join c.letter l
            join l.sender s
            join l.recipient r
            where """ + BOX_CONDITION + """
              and c.imageUrl is not null
              and ((:asSender = true and r.id = :folderMemberId) or (:asSender = false and s.id = :folderMemberId))
            order by l.sentAt desc, c.id desc
            """)
    List<String> findLatestFolderImageUrl(
            @Param("asSender") boolean asSender,
            @Param("memberId") Long memberId,
            @Param("folderMemberId") Long folderMemberId,
            Pageable pageable
    );

    @Query("""
            select l.sentAt as sentAt, c.imageUrl as imageUrl
            from LetterCard c
            join c.letter l
            join l.sender s
            join l.recipient r
            where """ + BOX_CONDITION + """
              and l.sentAt >= :startAt
              and l.sentAt < :endAt
            order by l.sentAt asc, c.id asc
            """)
    List<CalendarImageProjection> findBoxCalendarImages(
            @Param("asSender") boolean asSender,
            @Param("memberId") Long memberId,
            @Param("startAt") LocalDateTime startAt,
            @Param("endAt") LocalDateTime endAt
    );

    interface FolderSummaryProjection {
        Long getMemberId();

        String getNickname();

        long getCardCount();

        LocalDateTime getLatestSentAt();
    }

    interface CalendarImageProjection {
        LocalDateTime getSentAt();

        String getImageUrl();
    }
}
