package com.sdp1617.backend.archive.repository;

import com.sdp1617.backend.archive.entity.ArchiveCard;
import com.sdp1617.backend.archive.entity.ArchiveCategory;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
