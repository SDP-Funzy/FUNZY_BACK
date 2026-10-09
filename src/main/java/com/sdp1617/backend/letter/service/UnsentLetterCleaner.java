package com.sdp1617.backend.letter.service;

import com.sdp1617.backend.letter.entity.LetterStatus;
import com.sdp1617.backend.letter.repository.LetterRepository;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 오래 방치된 보내지 않은(작성 중·완료) 편지를 지운다 (#122). 카드·두들픽도 함께 지워지고,
 * 카드 사진은 이후 안 쓰는 사진 정리에서 지워진다. 보낸 편지는 대상이 아니다.
 */
@Component
@RequiredArgsConstructor
public class UnsentLetterCleaner {

    private static final Set<LetterStatus> UNSENT = EnumSet.of(LetterStatus.DRAFT, LetterStatus.COMPLETED);
    private static final int BATCH_SIZE = 100;

    private final LetterRepository letterRepository;
    private final TransactionTemplate transactionTemplate;

    /** cutoff 이전에 마지막으로 수정된 보내지 않은 편지를 지우고, 지운 개수를 돌려준다. */
    public int deleteNotUpdatedSince(LocalDateTime cutoff) {
        int deleted = 0;
        Long afterId = 0L;
        while (true) {
            List<Long> ids = letterRepository.findIdsByStatusInAndUpdatedAtBefore(
                    UNSENT, cutoff, afterId, PageRequest.of(0, BATCH_SIZE));
            if (ids.isEmpty()) {
                return deleted;
            }
            for (Long id : ids) {
                if (deleteIfStillStale(id, cutoff)) {
                    deleted++;
                }
            }
            afterId = ids.getLast();
        }
    }

    /**
     * 편지마다 짧은 트랜잭션으로 행을 잠그고 다시 확인한 뒤 지운다. 목록을 읽은 뒤 사용자가 이어 쓰거나 보냈으면 건너뛴다.
     */
    private boolean deleteIfStillStale(Long letterId, LocalDateTime cutoff) {
        Boolean deleted = transactionTemplate.execute(status -> letterRepository.findByIdForUpdate(letterId)
                .filter(letter -> UNSENT.contains(letter.getStatus()) && letter.getUpdatedAt().isBefore(cutoff))
                .map(letter -> {
                    letterRepository.delete(letter);
                    return true;
                })
                .orElse(false));
        return Boolean.TRUE.equals(deleted);
    }
}
