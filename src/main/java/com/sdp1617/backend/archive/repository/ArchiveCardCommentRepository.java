package com.sdp1617.backend.archive.repository;

import com.sdp1617.backend.archive.entity.ArchiveCardComment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ArchiveCardCommentRepository extends JpaRepository<ArchiveCardComment, Long> {

    @EntityGraph(attributePaths = "author")
    Page<ArchiveCardComment> findByArchiveCard_IdAndParentIsNull(Long archiveCardId, Pageable pageable);

    @EntityGraph(attributePaths = "author")
    Page<ArchiveCardComment> findByArchiveCard_IdAndParent_Id(Long archiveCardId, Long parentCommentId,
                                                              Pageable pageable);

    @EntityGraph(attributePaths = "author")
    Optional<ArchiveCardComment> findWithAuthorById(Long id);

    long countByArchiveCard_Id(Long archiveCardId);

    /** 원댓글별 답글 수. [원댓글 ID, 답글 수] */
    @Query("""
            select c.parent.id, count(c) from ArchiveCardComment c
            where c.parent.id in :parentCommentIds
            group by c.parent.id
            """)
    List<Object[]> countRepliesByParentCommentIds(@Param("parentCommentIds") Collection<Long> parentCommentIds);
}
