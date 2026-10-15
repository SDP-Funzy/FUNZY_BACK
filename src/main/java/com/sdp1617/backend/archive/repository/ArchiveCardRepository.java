package com.sdp1617.backend.archive.repository;

import com.sdp1617.backend.archive.entity.ArchiveCard;
import com.sdp1617.backend.archive.entity.ArchiveCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface ArchiveCardRepository extends JpaRepository<ArchiveCard, Long> {

    boolean existsByOwnerMemberIdAndLetterCardId(Long ownerMemberId, Long letterCardId);

    Optional<ArchiveCard> findByOwnerMemberIdAndLetterCardId(Long ownerMemberId, Long letterCardId);


    List<ArchiveCard> findByOwnerMemberIdOrderByCreatedAtDesc(Long ownerMemberId);

    List<ArchiveCard> findByOwnerMemberIdAndCategoryAndLockedFalseOrderByCreatedAtDesc(Long ownerMemberId, ArchiveCategory category);

    List<ArchiveCard> findByOwnerMemberIdAndLetterCardIdIn(Long ownerMemberId, Collection<Long> letterCardIds);

    /** 받은 편지함 목록에서 비공개로 콕한 카드가 든 편지 표시용 (#147). letterIds가 비어 있지 않을 때만 호출한다. */
    @Query("""
            select distinct c.letter.id from ArchiveCard a, LetterCard c
            where c.id = a.letterCardId and a.ownerMemberId = :ownerMemberId and a.locked = true
              and c.letter.id in :letterIds
            """)
    Set<Long> findLetterIdsWithLockedCards(@Param("ownerMemberId") Long ownerMemberId,
                                           @Param("letterIds") Collection<Long> letterIds);

    Optional<ArchiveCard> findByIdAndOwnerMemberId(Long id, Long ownerMemberId);

    void deleteByOwnerMemberId(Long ownerMemberId);

    void deleteByOwnerMemberIdAndLetterCardIdIn(Long ownerMemberId, Collection<Long> letterCardIds);

    /** 안 쓰는 사진 정리용 (#122). 콕한 카드는 편지 카드 사진의 주소를 복사해 같은 S3 파일을 쓴다. */
    @Query("select a.imageUrl from ArchiveCard a where a.imageUrl is not null")
    List<String> findAllImageUrls();
}
