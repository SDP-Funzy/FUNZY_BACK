package com.sdp1617.backend.card.repository;

import com.sdp1617.backend.card.entity.Card;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CardRepository extends JpaRepository<Card,Long> {
    @EntityGraph(attributePaths = {"envelop", "envelop.sender", "envelop.receiver"})
    Optional<Card> findByIdAndEnvelop_Sender_Id(Long id, Long senderId);

    @EntityGraph(attributePaths = {"envelop", "envelop.sender", "envelop.receiver"})
    Optional<Card> findByIdAndEnvelop_Receiver_Id(Long id, Long receiverId);

    @Query("""
            select c from Card c
            join fetch c.envelop e
            join fetch e.sender s
            join fetch e.receiver r
            where ((:asSender = true and e.sender.id = :memberId)
                   or (:asSender = false and e.receiver.id = :memberId))
              and (cast(:startAt as LocalDateTime) is null or c.createdAt >= :startAt)
              and (cast(:endAt as LocalDateTime) is null or c.createdAt < :endAt)
              and (cast(:keyword as String) is null
                   or lower(c.title) like :keyword
                   or lower(c.content) like :keyword
                   or lower(s.nickname) like :keyword
                   or lower(r.nickname) like :keyword)
              and (cast(:cursorCreatedAt as LocalDateTime) is null
                   or coalesce(c.createdAt, :emptyCreatedAt) < :cursorCreatedAt
                   or (coalesce(c.createdAt, :emptyCreatedAt) = :cursorCreatedAt and c.id < :cursorId))
            order by coalesce(c.createdAt, :emptyCreatedAt) desc, c.id desc
            """)
    List<Card> findCards(
            @Param("asSender") boolean asSender,
            @Param("memberId") Long memberId,
            @Param("startAt") LocalDateTime startAt,
            @Param("endAt") LocalDateTime endAt,
            @Param("keyword") String keyword,
            @Param("cursorCreatedAt") LocalDateTime cursorCreatedAt,
            @Param("cursorId") Long cursorId,
            @Param("emptyCreatedAt") LocalDateTime emptyCreatedAt,
            Pageable pageable
    );

    /**
     * 상대방별 폴더. 한쪽(보낸함이면 보낸 사람, 받은함이면 받은 사람)은 항상 나라서, 양쪽 회원으로 묶으면 상대방별로 묶인다.
     * GROUP BY에 파라미터가 들어간 CASE 식을 쓰면 PostgreSQL이 SELECT의 같은 식과 같다고 보지 않아 오류가 나므로 쓰지 않는다.
     */
    @Query("""
            select (case when :asSender = true then r.id else s.id end) as memberId,
                   (case when :asSender = true then r.nickname else s.nickname end) as nickname,
                   count(c) as cardCount,
                   max(coalesce(c.createdAt, :emptyCreatedAt)) as latestCardCreatedAt
            from Card c
            join c.envelop e
            join e.sender s
            join e.receiver r
            where (:asSender = true and e.sender.id = :memberId)
               or (:asSender = false and e.receiver.id = :memberId)
            group by s.id, s.nickname, r.id, r.nickname
            having (cast(:cursorCreatedAt as LocalDateTime) is null
                    or max(coalesce(c.createdAt, :emptyCreatedAt)) < :cursorCreatedAt
                    or (max(coalesce(c.createdAt, :emptyCreatedAt)) = :cursorCreatedAt
                        and case when :asSender = true then r.id else s.id end < :cursorId))
            order by max(coalesce(c.createdAt, :emptyCreatedAt)) desc,
                     case when :asSender = true then r.id else s.id end desc
            """)
    List<FolderSummaryProjection> findFolderSummaries(
            @Param("asSender") boolean asSender,
            @Param("memberId") Long memberId,
            @Param("cursorCreatedAt") LocalDateTime cursorCreatedAt,
            @Param("cursorId") Long cursorId,
            @Param("emptyCreatedAt") LocalDateTime emptyCreatedAt,
            Pageable pageable
    );

    @Query("""
            select c.imageUrl from Card c
            join c.envelop e
            where (:asSender = true and e.sender.id = :memberId and e.receiver.id = :folderMemberId)
               or (:asSender = false and e.receiver.id = :memberId and e.sender.id = :folderMemberId)
            order by coalesce(c.createdAt, :emptyCreatedAt) desc, c.id desc
            """)
    List<String> findLatestFolderImageUrl(
            @Param("asSender") boolean asSender,
            @Param("memberId") Long memberId,
            @Param("folderMemberId") Long folderMemberId,
            @Param("emptyCreatedAt") LocalDateTime emptyCreatedAt,
            Pageable pageable
    );

    @Query("""
            select c.createdAt as createdAt,
                   c.imageUrl as imageUrl
            from Card c
            join c.envelop e
            where ((:asSender = true and e.sender.id = :memberId)
                   or (:asSender = false and e.receiver.id = :memberId))
              and c.createdAt >= :startAt
              and c.createdAt < :endAt
            order by c.createdAt asc, c.id asc
            """)
    List<CalendarImageProjection> findCalendarImages(
            @Param("asSender") boolean asSender,
            @Param("memberId") Long memberId,
            @Param("startAt") LocalDateTime startAt,
            @Param("endAt") LocalDateTime endAt
    );

    interface FolderSummaryProjection {
        Long getMemberId();

        String getNickname();

        long getCardCount();

        LocalDateTime getLatestCardCreatedAt();
    }

    interface CalendarImageProjection {
        LocalDateTime getCreatedAt();

        String getImageUrl();
    }
}
