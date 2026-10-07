package com.sdp1617.backend.letter.service;

import com.sdp1617.backend.auth.entity.Member;
import com.sdp1617.backend.auth.repository.MemberRepository;
import com.sdp1617.backend.global.error.CustomException;
import com.sdp1617.backend.global.error.ErrorCode;
import com.sdp1617.backend.letter.dto.LetterPageResponse;
import com.sdp1617.backend.letter.dto.LetterResponse;
import com.sdp1617.backend.letter.dto.ReceivedLetterResponse;
import com.sdp1617.backend.letter.dto.SentLetterResponse;
import com.sdp1617.backend.letter.dto.UnreadLetterCountResponse;
import com.sdp1617.backend.letter.entity.Letter;
import com.sdp1617.backend.letter.entity.LetterSortType;
import com.sdp1617.backend.letter.entity.LetterStatus;
import com.sdp1617.backend.letter.repository.LetterRepository;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 편지 보내기·편지함 (#86). 보낸 사람과 받는 사람이 같은 편지를 보고(#82), 받는 사람이 지워도 보낸 편지함에는 남는다.
 * 볼 권한이 없는 편지는 존재 여부를 드러내지 않도록 없는 편지(LETTER_001)와 같이 응답한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LetterInboxService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final LetterRepository letterRepository;
    private final MemberRepository memberRepository;
    private final ReceivedLetterAccess receivedLetterAccess;
    private final ReceivedLetterReactionCleaner receivedLetterReactionCleaner;

    /** 완료한 편지를 회원에게 보낸다. 탈퇴하지 않은 회원이면 누구에게나 보낼 수 있고, 본인에게는 보낼 수 없다. */
    @Transactional
    public LetterResponse send(Long memberId, Long letterId, Long recipientMemberId) {
        if (recipientMemberId.equals(memberId)) {
            throw new CustomException(ErrorCode.LETTER_007);
        }
        Letter letter = letterRepository.findByIdForUpdate(letterId)
                .filter(found -> found.isWrittenBy(memberId))
                .orElseThrow(() -> new CustomException(ErrorCode.LETTER_001));
        Member recipient = memberRepository.findActiveById(recipientMemberId)
                .orElseThrow(() -> new CustomException(ErrorCode.LETTER_008));

        letter.sendTo(recipient);
        return LetterResponse.from(letter);
    }

    /**
     * 편지 열기. 보낸 사람은 언제든, 받는 사람은 받은 편지함에서 지우지 않은 동안 볼 수 있다.
     * 받는 사람이 처음 열면 읽은 시각을 남긴다.
     */
    @Transactional
    public LetterResponse getLetter(Long memberId, Long letterId) {
        Letter letter = letterRepository.findById(letterId)
                .orElseThrow(() -> new CustomException(ErrorCode.LETTER_001));
        if (letter.isVisibleToRecipient(memberId)) {
            letter.markReadByRecipient();
        } else if (!letter.isWrittenBy(memberId)) {
            throw new CustomException(ErrorCode.LETTER_001);
        }
        return LetterResponse.from(letter);
    }

    /**
     * 편지 지우기. 보낸 사람은 보내기 전 편지를 삭제(작성 취소)하고, 받는 사람은 받은 편지함에서 숨기며
     * 그 편지에 남긴 내 반응(아카이브·이모지·코멘트 등)도 함께 지운다 (LR-512).
     * 보낸 편지는 보낸 사람이 지울 수 없다(LETTER_003) — 받는 사람의 편지함에 있는 편지이기 때문이다.
     */
    @Transactional
    public void deleteOrHide(Long memberId, Long letterId) {
        Letter letter = letterRepository.findByIdForUpdate(letterId)
                .orElseThrow(() -> new CustomException(ErrorCode.LETTER_001));
        if (letter.isVisibleToRecipient(memberId)) {
            receivedLetterReactionCleaner.cleanMyReactions(memberId, letter);
            letter.hideForRecipient();
            return;
        }
        if (!letter.isWrittenBy(memberId)) {
            throw new CustomException(ErrorCode.LETTER_001);
        }
        if (letter.getStatus() == LetterStatus.SENT) {
            throw new CustomException(ErrorCode.LETTER_003);
        }
        letterRepository.delete(letter);
    }

    /** 받는 사람이 두들픽 선물을 고른다 (LR-022). 다시 고르면 바뀌고, null이면 선택 취소. */
    @Transactional
    public LetterResponse selectGift(Long memberId, Long letterId, Long giftItemId) {
        Letter letter = receivedLetterAccess.lockReceivedLetter(memberId, letterId);
        letter.selectGift(giftItemId);
        return LetterResponse.from(letter);
    }

    /** 받은 편지함 (LR-011/012): 보낸 사람(봉투 이름·닉네임) 검색, 받은 날짜 범위, 정렬. */
    public LetterPageResponse<ReceivedLetterResponse> getReceivedLetters(
            Long memberId, LetterSortType sortType, String senderName, LocalDate receivedFrom, LocalDate receivedTo,
            int page, int size
    ) {
        Sort sort = switch (sortType) {
            case LATEST -> Sort.by(Sort.Direction.DESC, "sentAt", "id");
            case OLDEST -> Sort.by(Sort.Direction.ASC, "sentAt", "id");
        };
        PageRequest pageable = PageRequest.of(Math.max(page, 0), normalizeSize(size), sort);
        return LetterPageResponse.of(
                letterRepository.findAll(receivedBy(memberId, senderName, receivedFrom, receivedTo), pageable),
                ReceivedLetterResponse::from);
    }

    /** 보낸 편지함 (LW-840). 최근에 보낸 순. */
    public LetterPageResponse<SentLetterResponse> getSentLetters(Long memberId, int page, int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), normalizeSize(size),
                Sort.by(Sort.Direction.DESC, "sentAt", "id"));
        return LetterPageResponse.of(
                letterRepository.findBySender_IdAndStatus(memberId, LetterStatus.SENT, pageable),
                SentLetterResponse::from);
    }

    /** 미읽음 편지 수 (LB-221): 받은 편지함에 남아 있고 아직 열지 않은 편지. */
    public UnreadLetterCountResponse getUnreadCount(Long memberId) {
        return new UnreadLetterCountResponse(letterRepository
                .countByRecipient_IdAndStatusAndRecipientHiddenAtIsNullAndReadAtIsNull(memberId, LetterStatus.SENT));
    }

    /** 조건이 비어 있으면 아예 넣지 않는다 — PostgreSQL에서 값 없는 파라미터의 타입을 추론하지 못하는 문제(#126)를 피한다. */
    private Specification<Letter> receivedBy(Long memberId, String senderName, LocalDate from, LocalDate to) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("recipient").get("id"), memberId));
            predicates.add(cb.equal(root.get("status"), LetterStatus.SENT));
            predicates.add(cb.isNull(root.get("recipientHiddenAt")));
            if (senderName != null && !senderName.isBlank()) {
                String pattern = "%" + escapeLike(senderName.trim().toLowerCase(Locale.ROOT)) + "%";
                Join<Letter, Member> sender = root.join("sender");
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("fromName")), pattern, '\\'),
                        cb.like(cb.lower(sender.get("nickname")), pattern, '\\')));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("sentAt"), from.atStartOfDay()));
            }
            if (to != null) {
                predicates.add(cb.lessThan(root.get("sentAt"), to.plusDays(1).atStartOfDay()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static int normalizeSize(int size) {
        return size < 1 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
