package com.sdp1617.backend.letter.service;

import com.sdp1617.backend.archive.entity.ArchiveCard;
import com.sdp1617.backend.archive.repository.ArchiveCardRepository;
import com.sdp1617.backend.auth.entity.Member;
import com.sdp1617.backend.auth.repository.MemberRepository;
import com.sdp1617.backend.global.error.CustomException;
import com.sdp1617.backend.global.error.ErrorCode;
import com.sdp1617.backend.letter.dto.LetterCardKokResponse;
import com.sdp1617.backend.letter.dto.LetterPageResponse;
import com.sdp1617.backend.letter.dto.LetterResponse;
import com.sdp1617.backend.letter.dto.ReceivedLetterResponse;
import com.sdp1617.backend.letter.dto.SentLetterResponse;
import com.sdp1617.backend.letter.dto.UnreadLetterCountResponse;
import com.sdp1617.backend.letter.entity.Letter;
import com.sdp1617.backend.letter.entity.LetterCard;
import com.sdp1617.backend.letter.entity.LetterSortType;
import com.sdp1617.backend.letter.entity.LetterStatus;
import com.sdp1617.backend.letter.repository.LetterRepository;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
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
    private final ArchiveCardRepository archiveCardRepository;
    private final ApplicationEventPublisher eventPublisher;

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
        eventPublisher.publishEvent(new LetterSentEvent(letter.getId(), memberId, recipient.getId(), letter.getFromName()));
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
            return recipientView(memberId, letter);
        }
        if (!letter.isWrittenBy(memberId)) {
            throw new CustomException(ErrorCode.LETTER_001);
        }
        return LetterResponse.from(letter);
    }

    /**
     * 편지 지우기. 보낸 사람은 보내기 전 편지를 삭제(작성 취소)하고, 받는 사람은 받은 편지함에서 숨기며
     * 그 편지에서 콕으로 담은 아카이브 카드와 그 편지로 받은 알림도 함께 지운다 (LR-512).
     * 보낸 편지는 보낸 사람이 지울 수 없다(LETTER_003) — 받는 사람의 편지함에 있는 편지이기 때문이다.
     * 단, 공유 링크로 보냈지만 아직 아무도 받지 않은 편지는 지울 수 있다 (링크도 함께 사라진다).
     */
    @Transactional
    public void deleteOrHide(Long memberId, Long letterId) {
        Letter letter = letterRepository.findByIdForUpdate(letterId)
                .orElseThrow(() -> new CustomException(ErrorCode.LETTER_001));
        if (letter.isVisibleToRecipient(memberId)) {
            deleteMyArchiveCards(memberId, letter);
            letter.hideForRecipient();
            eventPublisher.publishEvent(new ReceivedLetterHiddenEvent(letterId, memberId));
            return;
        }
        if (!letter.isWrittenBy(memberId)) {
            throw new CustomException(ErrorCode.LETTER_001);
        }
        // 공유 링크로 보냈지만 아직 아무도 받지 않은 편지는 지울 수 있다 — 받은 사람이 없어 다른 사람에게 영향이 없다.
        // 같은 편지 행을 잠그므로 링크 받기와 순서대로 처리된다 (먼저 받았으면 LETTER_003, 먼저 지웠으면 받기는 LETTER_011)
        if (letter.getStatus() == LetterStatus.SENT && !letter.isWaitingForRecipient()) {
            throw new CustomException(ErrorCode.LETTER_003);
        }
        letterRepository.delete(letter);
    }

    /**
     * 받는 사람이 두들픽 선물을 고른다 (LR-022). 다시 고르면 바뀌고, null이면 선택 취소.
     * 새로 고르거나 다른 선물로 바꿀 때 보낸 사람에게 알림 (LR-023). 취소하거나 같은 선물을 다시 고르면 알림 없음.
     */
    @Transactional
    public LetterResponse selectGift(Long memberId, Long letterId, Long giftItemId) {
        Letter letter = receivedLetterAccess.lockReceivedLetter(memberId, letterId);
        Long previousGiftItemId = letter.getSelectedGiftItemId();
        letter.selectGift(giftItemId);
        if (giftItemId != null && !giftItemId.equals(previousGiftItemId)) {
            eventPublisher.publishEvent(new GiftSelectedEvent(
                    letter.getId(), letter.getSender().getId(), memberId, letter.getToName()));
        }
        return recipientView(memberId, letter);
    }

    /** 받은 편지함 (LR-011/012): 보낸 사람(봉투 이름·닉네임) 검색, 받은 날짜 범위, 정렬, 잠긴편지(비공개로 콕한 카드가 든 편지, #147). */
    public LetterPageResponse<ReceivedLetterResponse> getReceivedLetters(
            Long memberId, LetterSortType sortType, String senderName, LocalDate receivedFrom, LocalDate receivedTo,
            boolean lockedOnly, int page, int size
    ) {
        Sort sort = switch (sortType) {
            case LATEST -> Sort.by(Sort.Direction.DESC, "sentAt", "id");
            case OLDEST -> Sort.by(Sort.Direction.ASC, "sentAt", "id");
        };
        PageRequest pageable = PageRequest.of(Math.max(page, 0), normalizeSize(size), sort);
        Page<Letter> letters = letterRepository.findAll(
                receivedBy(memberId, senderName, receivedFrom, receivedTo, lockedOnly), pageable);
        Set<Long> lockedLetterIds = lockedLetterIds(memberId, letters, lockedOnly);
        return LetterPageResponse.of(letters,
                letter -> ReceivedLetterResponse.from(letter, lockedLetterIds.contains(letter.getId())));
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
    private Specification<Letter> receivedBy(Long memberId, String senderName, LocalDate from, LocalDate to,
                                             boolean lockedOnly) {
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
            if (lockedOnly) {
                predicates.add(hasLockedCard(root, query, cb, memberId));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    /** 목록의 편지별 🔒 표시. 잠긴편지 필터로 찾은 목록이면 모두 잠긴 편지라 다시 조회하지 않는다. */
    private Set<Long> lockedLetterIds(Long memberId, Page<Letter> letters, boolean lockedOnly) {
        if (letters.isEmpty()) {
            return Set.of();
        }
        List<Long> letterIds = letters.map(Letter::getId).toList();
        return lockedOnly ? Set.copyOf(letterIds)
                : archiveCardRepository.findLetterIdsWithLockedCards(memberId, letterIds);
    }

    /**
     * 이 편지의 카드 중 내가 비공개로 콕한 카드가 있다 ({@code ArchiveCardRepository.findLetterIdsWithLockedCards}와 같은 기준).
     * 편지의 카드 컬렉션을 서브쿼리에서 correlate join하지 않고 LetterCard를 따로 두어, 페이지 count 쿼리에서도 단순하게 나오게 한다.
     */
    private static Predicate hasLockedCard(Root<Letter> root, CriteriaQuery<?> query, CriteriaBuilder cb, Long memberId) {
        Subquery<Long> locked = query.subquery(Long.class);
        Root<ArchiveCard> archiveCard = locked.from(ArchiveCard.class);
        Root<LetterCard> card = locked.from(LetterCard.class);
        locked.select(archiveCard.get("id")).where(
                cb.equal(card.get("letter"), root),
                cb.equal(archiveCard.get("letterCardId"), card.get("id")),
                cb.equal(archiveCard.get("ownerMemberId"), memberId),
                cb.isTrue(archiveCard.get("locked")));
        return cb.exists(locked);
    }

    /** 받는 사람이 보는 편지: 카드마다 내 콕 상태를 붙인다 (#147). */
    private LetterResponse recipientView(Long memberId, Letter letter) {
        List<Long> cardIds = letter.getCards().stream().map(LetterCard::getId).toList();
        Map<Long, LetterCardKokResponse> kokByCardId = cardIds.isEmpty() ? Map.of()
                : archiveCardRepository.findByOwnerMemberIdAndLetterCardIdIn(memberId, cardIds).stream()
                        .collect(Collectors.toMap(ArchiveCard::getLetterCardId, LetterCardKokResponse::from));
        return LetterResponse.from(letter, kokByCardId);
    }

    private static int normalizeSize(int size) {
        return size < 1 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
    }

    /** 받은 편지를 지울 때 그 편지에서 콕으로 담은 내 아카이브 카드를 지운다 (댓글은 DB에서 함께 지워진다). 편지를 잠근 뒤 호출한다 (콕 요청과 순서대로 처리되도록). */
    private void deleteMyArchiveCards(Long memberId, Letter letter) {
        List<Long> cardIds = letter.getCards().stream().map(LetterCard::getId).toList();
        if (!cardIds.isEmpty()) {
            archiveCardRepository.deleteByOwnerMemberIdAndLetterCardIdIn(memberId, cardIds);
        }
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
