package com.sdp1617.backend.letter.controller;

import com.sdp1617.backend.global.common.response.ApiResponse;
import com.sdp1617.backend.letter.dto.CardBoxType;
import com.sdp1617.backend.letter.dto.CardCalendarResponse;
import com.sdp1617.backend.letter.dto.CardFolderResponse;
import com.sdp1617.backend.letter.dto.CardImagePresignedUrlRequest;
import com.sdp1617.backend.letter.dto.CardImagePresignedUrlResponse;
import com.sdp1617.backend.letter.dto.CardStorageResponse;
import com.sdp1617.backend.letter.dto.CursorPageResponse;
import com.sdp1617.backend.letter.service.LetterCardBoxService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cards")
@RequiredArgsConstructor
@Tag(name = "마음카드 보관함", description = """
        주고받은 펀지 속 마음카드를 편지 단위가 아니라 카드 단위로, 보낸함·받은함으로 모아 보는 API (목록·상대방별 폴더·월별 캘린더).
        카드 사진 업로드 URL 발급도 여기 있습니다. 카드 작성·수정·삭제는 "편지 ① 쓰기" API를 사용합니다.
        """)
public class LetterCardBoxController {

    private final LetterCardBoxService letterCardBoxService;

    @PostMapping("/images/presigned-url")
    @Operation(summary = "카드 사진 업로드용 Presigned URL 발급", description = """
            편지 카드에 첨부할 사진을 S3에 직접 업로드할 수 있는 presigned URL을 발급합니다.
            - 지원 형식: image/jpeg, image/png, image/webp (그 외 CARD_003)
            - uploadUrl로 PUT 업로드한 뒤, imageKey를 편지 카드 추가·수정 요청에 넣습니다.
            """)
    public ApiResponse<CardImagePresignedUrlResponse> issueImagePresignedUrl(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId,
            @Valid @RequestBody CardImagePresignedUrlRequest request
    ) {
        return ApiResponse.ok("이미지 업로드 URL을 발급했습니다.", letterCardBoxService.issueImagePresignedUrl(memberId, request));
    }

    @GetMapping
    @Operation(summary = "마음카드 보관함 목록 조회", description = """
            보낸(SENT) 또는 받은(RECEIVED) 편지 속 카드를 주고받은 시각 최신순으로 조회합니다.
            - 받은함에는 받은 편지함에서 지운 편지의 카드가 나오지 않습니다.
            - date를 주면 그날 주고받은 카드만, keyword를 주면 내용 또는 상대방(받은함은 보낸 사람, 보낸함은 받은 사람)의 봉투 이름·닉네임에 포함된 카드만 조회합니다.
            - 커서 기반 페이지네이션: 응답의 nextCursor를 다음 요청의 cursor로 보내고, hasNext가 false면 마지막 페이지입니다.
            - size는 기본 20, 최대 50입니다. 잘못된 cursor는 COMMON_002입니다.
            """)
    public ApiResponse<CursorPageResponse<CardStorageResponse>> getCards(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId,
            @Parameter(description = "조회함 유형", example = "RECEIVED") @RequestParam(defaultValue = "RECEIVED") CardBoxType type,
            @Parameter(description = "주고받은 날짜", example = "2026-08-14")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @RequestParam(required = false) LocalDate date,
            @Parameter(description = "검색어", example = "노래") @RequestParam(required = false) String keyword,
            @Parameter(description = "다음 페이지 조회용 커서", example = "MjAyNi0wOC0xNFQxMzowMDowMHwx")
            @RequestParam(required = false) String cursor,
            @Parameter(description = "페이지 크기", example = "20") @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.ok("마음카드 목록을 조회했습니다.",
                letterCardBoxService.getCards(memberId, type, date, keyword, cursor, size));
    }

    @GetMapping("/folders")
    @Operation(summary = "마음카드 보관함 폴더 조회", description = """
            주고받은 상대방별로 묶은 폴더 목록을 최근에 주고받은 순으로 조회합니다.
            - 폴더마다 상대 회원 ID·닉네임, 카드 수, 최근 카드 사진, 최근에 주고받은 시각을 반환합니다.
            - 페이지네이션 방식은 보관함 목록 조회와 같습니다 (size 기본 20, 최대 50).
            """)
    public ApiResponse<CursorPageResponse<CardFolderResponse>> getFolders(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId,
            @Parameter(description = "조회함 유형", example = "RECEIVED") @RequestParam(defaultValue = "RECEIVED") CardBoxType type,
            @Parameter(description = "다음 페이지 조회용 커서", example = "MjAyNi0wOC0xNFQxMzowMDowMHwx")
            @RequestParam(required = false) String cursor,
            @Parameter(description = "페이지 크기", example = "20") @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.ok("마음카드 폴더를 조회했습니다.", letterCardBoxService.getFolders(memberId, type, cursor, size));
    }

    @GetMapping("/calendar")
    @Operation(summary = "마음카드 보관함 월별 캘린더 요약 조회", description = """
            해당 월에 카드를 주고받은 날짜와, 날짜별 카드 사진 URL 목록을 조회합니다.
            - 카드가 없는 날짜는 days에 포함되지 않습니다. 사진이 없는 카드는 사진 목록에서 제외됩니다.
            """)
    public ApiResponse<CardCalendarResponse> getCalendar(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId,
            @Parameter(description = "조회함 유형", example = "RECEIVED") @RequestParam(defaultValue = "RECEIVED") CardBoxType type,
            @Parameter(description = "조회 연도", example = "2026") @RequestParam int year,
            @Parameter(description = "조회 월", example = "8") @RequestParam int month
    ) {
        return ApiResponse.ok("마음카드 캘린더를 조회했습니다.", letterCardBoxService.getCalendar(memberId, type, year, month));
    }

    @GetMapping("/{cardId}")
    @Operation(summary = "보관함 카드 1장 조회", description = """
            내가 보냈거나 받은 편지 속 카드 1장을 조회합니다.
            - 보내기 전 편지의 카드, 받은 편지함에서 지운 편지의 카드, 남의 카드는 CARD_001입니다.
            """)
    public ApiResponse<CardStorageResponse> getCard(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId,
            @Parameter(description = "카드 ID", example = "10") @PathVariable Long cardId
    ) {
        return ApiResponse.ok("마음카드를 조회했습니다.", letterCardBoxService.getCard(memberId, cardId));
    }
}
