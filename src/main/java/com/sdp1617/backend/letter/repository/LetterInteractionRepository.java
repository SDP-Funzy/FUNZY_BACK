package com.sdp1617.backend.letter.repository;

import com.sdp1617.backend.letter.entity.LetterInteraction;
import com.sdp1617.backend.letter.entity.LetterInteractionType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LetterInteractionRepository extends JpaRepository<LetterInteraction, Long> {

    boolean existsByLetterIdAndMemberIdAndTypeAndValue(
            Long letterId,
            Long memberId,
            LetterInteractionType type,
            String value
    );

    void deleteByLetterId(Long letterId);

    void deleteByMemberIdAndType(Long memberId, LetterInteractionType type);
}
