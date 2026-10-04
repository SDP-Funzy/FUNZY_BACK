package com.sdp1617.backend.card.service;

import com.sdp1617.backend.auth.entity.Member;
import com.sdp1617.backend.auth.repository.MemberRepository;
import com.sdp1617.backend.card.dto.CardBoxType;
import com.sdp1617.backend.card.dto.request.CardCreateRequest;
import com.sdp1617.backend.card.dto.request.CardImagePresignedUrlRequest;
import com.sdp1617.backend.card.dto.request.CardImageUploadCompleteRequest;
import com.sdp1617.backend.card.dto.response.CardCalendarDayResponse;
import com.sdp1617.backend.card.dto.response.CardCalendarResponse;
import com.sdp1617.backend.card.dto.response.CardFolderResponse;
import com.sdp1617.backend.card.dto.response.CardImagePresignedUrlResponse;
import com.sdp1617.backend.card.dto.response.CardListResponse;
import com.sdp1617.backend.card.dto.response.CardResponse;
import com.sdp1617.backend.card.dto.response.CardStorageResponse;
import com.sdp1617.backend.card.dto.response.CursorPageResponse;
import com.sdp1617.backend.card.entity.Card;
import com.sdp1617.backend.card.entity.Envelop;
import com.sdp1617.backend.card.repository.CardRepository;
import com.sdp1617.backend.card.repository.CardRepository.CalendarImageProjection;
import com.sdp1617.backend.card.repository.CardRepository.FolderSummaryProjection;
import com.sdp1617.backend.card.repository.EnvelopRepository;
import com.sdp1617.backend.global.error.CustomException;
import com.sdp1617.backend.global.error.ErrorCode;
import com.sdp1617.backend.global.s3.S3ImageService;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CardService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 50;
    private static final LocalDateTime EMPTY_CREATED_AT = LocalDateTime.MIN;

    private final EnvelopRepository envelopRepository;
    private final CardRepository cardRepository;
    private final MemberRepository memberRepository;
    private final EntityManager entityManager;
    private final S3ImageService s3ImageService;
    private final TransactionTemplate transactionTemplate;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    public CardImagePresignedUrlResponse issueImagePresignedUrl(
            Long senderId,
            CardImagePresignedUrlRequest request
    ) {
        S3ImageService.PresignedUpload upload = s3ImageService.issuePresignedUpload(
                imageKeyPrefix(senderId), request.contentType(), ErrorCode.CARD_003);
        return CardImagePresignedUrlResponse.from(upload);
    }

    public CursorPageResponse<CardStorageResponse> getCards(
            Long memberId,
            CardBoxType type,
            LocalDate date,
            String keyword,
            String cursor,
            int size
    ) {
        CursorKey cursorKey = parseCursor(cursor);
        int pageSize = normalizePageSize(size);
        List<CardStorageResponse> cards = findCardsByType(
                memberId,
                type,
                dateStartAt(date),
                dateEndAt(date),
                normalizeKeyword(keyword),
                cursorKey,
                PageRequest.of(0, pageSize + 1)
        )
                .stream()
                .map(CardStorageResponse::from)
                .toList();

        return toCursorPage(
                cards,
                pageSize,
                response -> new CursorKey(response.createdAt(), response.cardId())
        );
    }

    public CursorPageResponse<CardFolderResponse> getFolders(
            Long memberId,
            CardBoxType type,
            String cursor,
            int size
    ) {
        CursorKey cursorKey = parseCursor(cursor);
        int pageSize = normalizePageSize(size);
        List<CardFolderResponse> folders = findFolderSummaries(
                memberId,
                type,
                cursorKey,
                PageRequest.of(0, pageSize + 1)
        );

        return toCursorPage(
                folders,
                pageSize,
                response -> new CursorKey(response.latestCardCreatedAt(), response.memberId())
        );
    }

    public CardCalendarResponse getCalendar(Long memberId, CardBoxType type, int year, int month) {
        YearMonth yearMonth = YearMonth.of(year, month);
        LocalDateTime startAt = yearMonth.atDay(1).atStartOfDay();
        LocalDateTime endAt = yearMonth.plusMonths(1).atDay(1).atStartOfDay();
        Map<LocalDate, List<String>> imageUrlsByDate = findCalendarImages(memberId, type, startAt, endAt)
                .stream()
                .collect(Collectors.groupingBy(
                        image -> image.getCreatedAt().toLocalDate(),
                        LinkedHashMap::new,
                        Collectors.mapping(CalendarImageProjection::getImageUrl, Collectors.toList())
                ));

        List<CardCalendarDayResponse> days = imageUrlsByDate.entrySet()
                .stream()
                .map(entry -> new CardCalendarDayResponse(
                        entry.getKey(),
                        entry.getValue()
                                .stream()
                                .filter(imageUrl -> imageUrl != null && !imageUrl.isBlank())
                                .toList()
                ))
                .toList();

        return new CardCalendarResponse(type, year, month, days);
    }

    public CardStorageResponse getCard(Long memberId, Long cardId) {
        return CardStorageResponse.from(findAccessibleCard(memberId, cardId));
    }

    /**
     * S3 HEAD/GET 검증을 트랜잭션 밖에서 먼저 끝낸 뒤, DB 쓰기만 별도의 짧은 트랜잭션으로 묶는다.
     * (같은 클래스 내 @Transactional 메서드를 this로 호출하면 프록시를 안 타므로 TransactionTemplate을 사용)
     * 주의: NOT_SUPPORTED가 외부 트랜잭션을 중단시키고 TransactionTemplate이 별도로 즉시 커밋하므로,
     * 이미 트랜잭션이 진행 중인 다른 @Transactional 메서드 안에서 이 메서드를 호출하면 안 된다(원자성이 깨짐).
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public CardListResponse completeImageUpload(
            Long senderId,
            Long cardId,
            CardImageUploadCompleteRequest request
    ) {
        validateImageKeyOwner(senderId, request.imageKey());
        validateUploadedImage(request.imageKey());
        return transactionTemplate.execute(status -> {
            Card card = findWrittenCard(senderId, cardId);
            card.updateImage(request.imageKey(), createImageUrl(request.imageKey()));
            return CardListResponse.from(card);
        });
    }

    @Transactional
    public void deleteWrittenCard(Long senderId, Long cardId) {
        Card card = findWrittenCard(senderId, cardId);
        cardRepository.delete(card);
    }

    /**
     * S3 HEAD/GET 검증을 트랜잭션 밖에서 먼저 끝낸 뒤, DB 쓰기만 별도의 짧은 트랜잭션으로 묶는다.
     * (같은 클래스 내 @Transactional 메서드를 this로 호출하면 프록시를 안 타므로 TransactionTemplate을 사용)
     * 주의: NOT_SUPPORTED가 외부 트랜잭션을 중단시키고 TransactionTemplate이 별도로 즉시 커밋하므로,
     * 이미 트랜잭션이 진행 중인 다른 @Transactional 메서드 안에서 이 메서드를 호출하면 안 된다(원자성이 깨짐).
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public CardResponse createCard(Long senderId, CardCreateRequest request){
        validateEnvelopCreateFields(senderId, request);
        String imageKey = normalizeImageKey(request.imageKey());
        if (imageKey != null) {
            validateImageKeyOwner(senderId, imageKey);
            validateUploadedImage(imageKey);
        }

        return transactionTemplate.execute(status -> {
            // 받는 사람이 없거나 탈퇴했으면 거절한다. 탈퇴해도 회원 행과 기존 봉투는 남으므로(익명화) 봉투가 있어도 확인한다.
            if (!memberRepository.existsActiveById(request.receiverId())) {
                throw new CustomException(ErrorCode.CARD_006);
            }
            Envelop envelop = envelopRepository.findBySender_IdAndReceiver_Id(senderId, request.receiverId())
                    .orElseGet(() -> createEnvelop(senderId, request));

            Card card = Card.create(
                    envelop,
                    request.title(),
                    request.category(),
                    request.link(),
                    request.linkTitle(),
                    request.content()
            );
            if (imageKey != null) {
                card.updateImage(imageKey, createImageUrl(imageKey));
            }
            Card savedCard = cardRepository.save(card);

            return CardResponse.from(savedCard, createShareUrl(savedCard.getId()));
        });
    }

    private String normalizeImageKey(String imageKey) {
        return (imageKey == null || imageKey.isBlank()) ? null : imageKey;
    }

    private Envelop createEnvelop(Long senderId, CardCreateRequest request) {
        Member sender = entityManager.getReference(Member.class, senderId);
        Member receiver = entityManager.getReference(Member.class, request.receiverId());

        Envelop envelop = Envelop.create(sender, receiver, request.designType());
        return envelopRepository.save(envelop);
    }

    private void validateEnvelopCreateFields(Long senderId, CardCreateRequest request) {
        if (request.receiverId() == null || request.designType() == null) {
            throw new CustomException(
                    ErrorCode.COMMON_002,
                    "receiverId와 designType은 봉투 생성 시 필수입니다."
            );
        }
        if (request.receiverId().equals(senderId)) {
            throw new CustomException(ErrorCode.CARD_005);
        }
    }

    private Card findWrittenCard(Long senderId, Long cardId) {
        return cardRepository.findByIdAndEnvelop_Sender_Id(cardId, senderId)
                .orElseThrow(() -> new CustomException(ErrorCode.CARD_001));
    }

    private Card findAccessibleCard(Long memberId, Long cardId) {
        return cardRepository.findByIdAndEnvelop_Sender_Id(cardId, memberId)
                .or(() -> cardRepository.findByIdAndEnvelop_Receiver_Id(cardId, memberId))
                .orElseThrow(() -> new CustomException(ErrorCode.CARD_001));
    }

    private List<Card> findCardsByType(
            Long memberId,
            CardBoxType type,
            LocalDateTime startAt,
            LocalDateTime endAt,
            String keyword,
            CursorKey cursorKey,
            Pageable pageable
    ) {
        return cardRepository.findCards(
                asSender(type),
                memberId,
                startAt,
                endAt,
                keyword,
                cursorCreatedAt(cursorKey),
                cursorId(cursorKey),
                EMPTY_CREATED_AT,
                pageable
        );
    }

    private List<CardFolderResponse> findFolderSummaries(
            Long memberId,
            CardBoxType type,
            CursorKey cursorKey,
            Pageable pageable
    ) {
        List<FolderSummaryProjection> summaries = cardRepository.findFolderSummaries(
                asSender(type),
                memberId,
                cursorCreatedAt(cursorKey),
                cursorId(cursorKey),
                EMPTY_CREATED_AT,
                pageable
        );

        return summaries.stream()
                .map(summary -> new CardFolderResponse(
                        summary.getMemberId(),
                        summary.getNickname(),
                        summary.getCardCount(),
                        findLatestFolderImageUrl(memberId, type, summary.getMemberId()),
                        restoreEmptyCreatedAt(summary.getLatestCardCreatedAt())
                ))
                .toList();
    }

    private String findLatestFolderImageUrl(Long memberId, CardBoxType type, Long folderMemberId) {
        List<String> imageUrls = cardRepository.findLatestFolderImageUrl(
                asSender(type),
                memberId,
                folderMemberId,
                EMPTY_CREATED_AT,
                PageRequest.of(0, 1)
        );
        return imageUrls.isEmpty() ? null : imageUrls.get(0);
    }

    private List<CalendarImageProjection> findCalendarImages(
            Long memberId,
            CardBoxType type,
            LocalDateTime startAt,
            LocalDateTime endAt
    ) {
        return cardRepository.findCalendarImages(asSender(type), memberId, startAt, endAt);
    }

    private boolean asSender(CardBoxType type) {
        return type == CardBoxType.SENT;
    }

    private LocalDateTime dateStartAt(LocalDate date) {
        return date == null ? null : date.atStartOfDay();
    }

    private LocalDateTime dateEndAt(LocalDate date) {
        return date == null ? null : date.plusDays(1).atStartOfDay();
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
    }

    private LocalDateTime cursorCreatedAt(CursorKey cursorKey) {
        return cursorKey == null ? null : cursorKey.createdAt();
    }

    private Long cursorId(CursorKey cursorKey) {
        return cursorKey == null ? null : cursorKey.id();
    }

    private LocalDateTime restoreEmptyCreatedAt(LocalDateTime createdAt) {
        return EMPTY_CREATED_AT.equals(createdAt) ? null : createdAt;
    }

    private void validateUploadedImage(String imageKey) {
        s3ImageService.validateUploadedImage(imageKey, ErrorCode.CARD_002, ErrorCode.CARD_003, ErrorCode.CARD_004);
    }

    private void validateImageKeyOwner(Long senderId, String imageKey) {
        s3ImageService.validateOwnership(imageKey, imageKeyPrefix(senderId));
    }

    private String imageKeyPrefix(Long senderId) {
        return "cards/" + senderId + "/";
    }

    private String createImageUrl(String imageKey) {
        return s3ImageService.buildImageUrl(imageKey);
    }

    private String createShareUrl(Long cardId) {
        return frontendUrl.replaceAll("/$", "") + "/cards/" + cardId;
    }

    private int normalizePageSize(int size) {
        if (size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    private <T> CursorPageResponse<T> toCursorPage(
            List<T> items,
            int size,
            Function<T, CursorKey> cursorExtractor
    ) {
        boolean hasNext = items.size() > size;
        List<T> pageItems = hasNext ? items.subList(0, size) : items;
        String nextCursor = hasNext ? encodeCursor(cursorExtractor.apply(pageItems.get(pageItems.size() - 1))) : null;
        return new CursorPageResponse<>(pageItems, nextCursor, hasNext);
    }

    private String encodeCursor(CursorKey cursorKey) {
        String plainCursor = normalizeCreatedAt(cursorKey.createdAt()).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                + "|" + cursorKey.id();
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(plainCursor.getBytes(StandardCharsets.UTF_8));
    }

    private CursorKey parseCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            String plainCursor = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] parts = plainCursor.split("\\|", 2);
            return new CursorKey(
                    LocalDateTime.parse(parts[0], DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                    Long.parseLong(parts[1])
            );
        } catch (RuntimeException exception) {
            throw new CustomException(ErrorCode.COMMON_002, "cursor 값이 올바르지 않습니다.");
        }
    }

    private LocalDateTime normalizeCreatedAt(LocalDateTime createdAt) {
        return createdAt == null ? EMPTY_CREATED_AT : createdAt;
    }

    private record CursorKey(
            LocalDateTime createdAt,
            Long id
    ) {
        private CursorKey {
            createdAt = createdAt == null ? EMPTY_CREATED_AT : createdAt;
        }
    }
}
