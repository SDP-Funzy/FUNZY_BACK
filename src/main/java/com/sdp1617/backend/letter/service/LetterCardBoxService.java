package com.sdp1617.backend.letter.service;

import com.sdp1617.backend.global.error.CustomException;
import com.sdp1617.backend.global.error.ErrorCode;
import com.sdp1617.backend.global.s3.S3ImageService;
import com.sdp1617.backend.letter.dto.CardBoxType;
import com.sdp1617.backend.letter.dto.CardCalendarDayResponse;
import com.sdp1617.backend.letter.dto.CardCalendarResponse;
import com.sdp1617.backend.letter.dto.CardFolderResponse;
import com.sdp1617.backend.letter.dto.CardImagePresignedUrlRequest;
import com.sdp1617.backend.letter.dto.CardImagePresignedUrlResponse;
import com.sdp1617.backend.letter.dto.CardStorageResponse;
import com.sdp1617.backend.letter.dto.CursorPageResponse;
import com.sdp1617.backend.letter.entity.Letter;
import com.sdp1617.backend.letter.entity.LetterCard;
import com.sdp1617.backend.letter.entity.LetterStatus;
import com.sdp1617.backend.letter.repository.LetterCardRepository;
import com.sdp1617.backend.letter.repository.LetterCardRepository.CalendarImageProjection;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 마음카드 보관함 (#82): 주고받은 편지 속 카드를 보낸함·받은함으로 모아 본다. 목록·상대방별 폴더·월별 캘린더.
 * 보낸 편지의 카드만 나오고, 받은함에서는 받은 편지함에서 지운 편지의 카드가 빠진다.
 * 정렬과 날짜는 카드를 주고받은 시각(편지를 보낸 시각) 기준이다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LetterCardBoxService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 50;
    private static final String IMAGE_KEY_PREFIX = "cards/";

    private final LetterCardRepository letterCardRepository;
    private final S3ImageService s3ImageService;

    /** 카드 사진 업로드용 presigned URL. 편지 카드 추가·수정에 imageKey로 넣는다. */
    public CardImagePresignedUrlResponse issueImagePresignedUrl(Long memberId, CardImagePresignedUrlRequest request) {
        S3ImageService.PresignedUpload upload = s3ImageService.issuePresignedUpload(
                IMAGE_KEY_PREFIX + memberId + "/", request.contentType(), ErrorCode.CARD_003);
        return CardImagePresignedUrlResponse.from(upload);
    }

    public CursorPageResponse<CardStorageResponse> getCards(
            Long memberId, CardBoxType type, LocalDate date, String keyword, String cursor, int size
    ) {
        CursorKey cursorKey = parseCursor(cursor);
        int pageSize = normalizePageSize(size);
        List<CardStorageResponse> cards = letterCardRepository.findBoxCards(
                        asSender(type), memberId,
                        date == null ? null : date.atStartOfDay(),
                        date == null ? null : date.plusDays(1).atStartOfDay(),
                        toKeywordPattern(keyword),
                        cursorKey == null ? null : cursorKey.sentAt(),
                        cursorKey == null ? null : cursorKey.id(),
                        PageRequest.of(0, pageSize + 1))
                .stream()
                .map(CardStorageResponse::from)
                .toList();
        return toCursorPage(cards, pageSize, card -> new CursorKey(card.sentAt(), card.cardId()));
    }

    public CursorPageResponse<CardFolderResponse> getFolders(Long memberId, CardBoxType type, String cursor, int size) {
        CursorKey cursorKey = parseCursor(cursor);
        int pageSize = normalizePageSize(size);
        List<CardFolderResponse> folders = letterCardRepository.findBoxFolders(
                        asSender(type), memberId,
                        cursorKey == null ? null : cursorKey.sentAt(),
                        cursorKey == null ? null : cursorKey.id(),
                        PageRequest.of(0, pageSize + 1))
                .stream()
                .map(summary -> new CardFolderResponse(
                        summary.getMemberId(),
                        summary.getNickname(),
                        summary.getCardCount(),
                        latestFolderImageUrl(memberId, type, summary.getMemberId()),
                        summary.getLatestSentAt()))
                .toList();
        return toCursorPage(folders, pageSize, folder -> new CursorKey(folder.latestSentAt(), folder.memberId()));
    }

    public CardCalendarResponse getCalendar(Long memberId, CardBoxType type, int year, int month) {
        YearMonth yearMonth = YearMonth.of(year, month);
        Map<LocalDate, List<String>> imageUrlsByDate = letterCardRepository.findBoxCalendarImages(
                        asSender(type), memberId,
                        yearMonth.atDay(1).atStartOfDay(),
                        yearMonth.plusMonths(1).atDay(1).atStartOfDay())
                .stream()
                .collect(Collectors.groupingBy(
                        image -> image.getSentAt().toLocalDate(),
                        LinkedHashMap::new,
                        Collectors.mapping(CalendarImageProjection::getImageUrl, Collectors.toList())));
        List<CardCalendarDayResponse> days = imageUrlsByDate.entrySet().stream()
                .map(entry -> new CardCalendarDayResponse(entry.getKey(), entry.getValue().stream()
                        .filter(imageUrl -> imageUrl != null && !imageUrl.isBlank())
                        .toList()))
                .toList();
        return new CardCalendarResponse(type, year, month, days);
    }

    /**
     * 보관함의 카드 1장: 내가 보낸 편지의 카드이거나, 내가 받고 숨기지 않은 편지의 카드.
     * 공유 링크로 보냈지만 아직 받는 사람이 없는 편지는 목록·폴더·캘린더처럼 보관함에 넣지 않는다 (#87, 받는 사람 정보가 없음).
     */
    public CardStorageResponse getCard(Long memberId, Long cardId) {
        LetterCard card = letterCardRepository.findWithLetterById(cardId)
                .orElseThrow(() -> new CustomException(ErrorCode.CARD_001));
        Letter letter = card.getLetter();
        boolean sentByMe = letter.getStatus() == LetterStatus.SENT && letter.isWrittenBy(memberId)
                && !letter.isWaitingForRecipient();
        if (!sentByMe && !letter.isVisibleToRecipient(memberId)) {
            throw new CustomException(ErrorCode.CARD_001);
        }
        return CardStorageResponse.from(card);
    }

    private String latestFolderImageUrl(Long memberId, CardBoxType type, Long folderMemberId) {
        List<String> imageUrls = letterCardRepository.findLatestFolderImageUrl(
                asSender(type), memberId, folderMemberId, PageRequest.of(0, 1));
        return imageUrls.isEmpty() ? null : imageUrls.get(0);
    }

    private static boolean asSender(CardBoxType type) {
        return type == CardBoxType.SENT;
    }

    /** 검색어의 LIKE 와일드카드(%, _)는 글자 그대로 찾도록 이스케이프한다. */
    private static String toKeywordPattern(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        String escaped = keyword.trim().toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + escaped + "%";
    }

    private static int normalizePageSize(int size) {
        return size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
    }

    private static <T> CursorPageResponse<T> toCursorPage(List<T> items, int size, Function<T, CursorKey> cursorOf) {
        boolean hasNext = items.size() > size;
        List<T> pageItems = hasNext ? items.subList(0, size) : items;
        String nextCursor = hasNext ? encodeCursor(cursorOf.apply(pageItems.get(pageItems.size() - 1))) : null;
        return new CursorPageResponse<>(pageItems, nextCursor, hasNext);
    }

    private static String encodeCursor(CursorKey cursorKey) {
        String plain = cursorKey.sentAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + "|" + cursorKey.id();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(plain.getBytes(StandardCharsets.UTF_8));
    }

    private static CursorKey parseCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            String[] parts = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8).split("\\|", 2);
            return new CursorKey(LocalDateTime.parse(parts[0], DateTimeFormatter.ISO_LOCAL_DATE_TIME), Long.parseLong(parts[1]));
        } catch (RuntimeException exception) {
            throw new CustomException(ErrorCode.COMMON_002, "cursor 값이 올바르지 않습니다.");
        }
    }

    /** 다음 페이지 위치: 마지막 항목의 주고받은 시각과 ID(같은 시각이면 ID로 구분). */
    private record CursorKey(LocalDateTime sentAt, Long id) {
    }
}
