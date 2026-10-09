package com.sdp1617.backend.archive.repository;

import com.sdp1617.backend.archive.entity.ArchiveCard;
import com.sdp1617.backend.archive.entity.ArchiveCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ArchiveCardRepository extends JpaRepository<ArchiveCard, Long> {

    boolean existsByOwnerMemberIdAndLetterCardId(Long ownerMemberId, Long letterCardId);

    Optional<ArchiveCard> findByOwnerMemberIdAndLetterCardId(Long ownerMemberId, Long letterCardId);


    List<ArchiveCard> findByOwnerMemberIdOrderByCreatedAtDesc(Long ownerMemberId);

    List<ArchiveCard> findByOwnerMemberIdAndCategoryOrderByCreatedAtDesc(Long ownerMemberId, ArchiveCategory category);

    Optional<ArchiveCard> findByIdAndOwnerMemberId(Long id, Long ownerMemberId);

    void deleteByOwnerMemberId(Long ownerMemberId);

    List<ArchiveCard> findByOwnerMemberIdAndLetterCardIdIn(Long ownerMemberId, Collection<Long> letterCardIds);

    /** 안 쓰는 사진 정리용 (#122). 콕한 카드는 편지 카드 사진의 주소를 복사해 같은 S3 파일을 쓴다. */
    @Query("select a.imageUrl from ArchiveCard a where a.imageUrl is not null")
    List<String> findAllImageUrls();
}
