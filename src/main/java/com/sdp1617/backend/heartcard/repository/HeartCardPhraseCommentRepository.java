package com.sdp1617.backend.heartcard.repository;

import com.sdp1617.backend.heartcard.entity.HeartCardPhraseComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Collection;

public interface HeartCardPhraseCommentRepository extends JpaRepository<HeartCardPhraseComment, Long> {

    List<HeartCardPhraseComment> findByHeartCardIdOrderByStartOffsetAsc(Long heartCardId);

    Optional<HeartCardPhraseComment> findByIdAndHeartCardId(Long id, Long heartCardId);


    @Query("""
            select count(comment) > 0
            from HeartCardPhraseComment comment
            where comment.heartCardId = :heartCardId
              and comment.startOffset < :endOffset
              and comment.endOffset > :startOffset
            """)
    boolean existsOverlappingRange(
            @Param("heartCardId") Long heartCardId,
            @Param("startOffset") int startOffset,
            @Param("endOffset") int endOffset
    );

    void deleteByMemberIdAndHeartCardIdIn(Long memberId, Collection<Long> heartCardIds);
}
