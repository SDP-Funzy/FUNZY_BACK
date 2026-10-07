package com.sdp1617.backend.letter.repository;

import com.sdp1617.backend.letter.entity.GiftItem;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GiftItemRepository extends JpaRepository<GiftItem, Long> {

    /** 선물 후보와 소속 편지를 함께 읽는다 (받는 사람 확인용). */
    @EntityGraph(attributePaths = "letter")
    Optional<GiftItem> findWithLetterById(Long id);

    /** 소속 편지 ID만 읽는다 — 편지를 잠그기 전에 엔티티를 미리 읽어 두면 잠금 후에도 예전 상태를 보게 되므로. */
    @Query("select x.letter.id from GiftItem x where x.id = :id")
    Optional<Long> findLetterIdById(@Param("id") Long id);
}
