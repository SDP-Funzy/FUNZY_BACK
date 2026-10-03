package com.sdp1617.backend.card.controller;

import com.sdp1617.backend.card.dto.CardBoxType;
import com.sdp1617.backend.card.dto.request.CardCreateRequest;
import com.sdp1617.backend.card.dto.request.CardImagePresignedUrlRequest;
import com.sdp1617.backend.card.dto.request.CardImageUploadCompleteRequest;
import com.sdp1617.backend.card.dto.response.CardCalendarResponse;
import com.sdp1617.backend.card.dto.response.CardFolderResponse;
import com.sdp1617.backend.card.dto.response.CardImagePresignedUrlResponse;
import com.sdp1617.backend.card.dto.response.CardListResponse;
import com.sdp1617.backend.card.dto.response.CardResponse;
import com.sdp1617.backend.card.dto.response.CardStorageResponse;
import com.sdp1617.backend.card.dto.response.CursorPageResponse;
import com.sdp1617.backend.card.service.CardService;
import com.sdp1617.backend.global.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/cards")
@RequiredArgsConstructor
@Tag(name = "마음카드 API")
public class CardController {

    private final CardService cardService;

    @PostMapping("/images/presigned-url")
    @Operation(summary="마음카드 이미지 업로드용 Presigned URL 발급")
    public ApiResponse<CardImagePresignedUrlResponse> issueImagePresignedUrl(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId,
            @Valid @RequestBody CardImagePresignedUrlRequest request
    ){
        return ApiResponse.ok("이미지 업로드 URL을 발급했습니다.", cardService.issueImagePresignedUrl(memberId, request));
    }

    @GetMapping
    @Operation(summary="마음카드 보관함 목록 조회")
    public ApiResponse<CursorPageResponse<CardStorageResponse>> getCards(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId,
            @Parameter(description = "조회함 유형", example = "RECEIVED") @RequestParam(defaultValue = "SENT") CardBoxType type,
            @Parameter(description = "조회 날짜", example = "2026-08-14")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @RequestParam(required = false) LocalDate date,
            @Parameter(description = "검색어. 제목, 내용, 발신자/수신자 닉네임 검색", example = "YESEUNG")
            @RequestParam(required = false) String keyword,
            @Parameter(description = "다음 페이지 조회용 커서", example = "MjAyNi0wOC0xNFQxMzowMDowMHwx")
            @RequestParam(required = false) String cursor,
            @Parameter(description = "페이지 크기", example = "20") @RequestParam(defaultValue = "20") int size
    ){
        return ApiResponse.ok("마음카드 목록을 조회했습니다.", cardService.getCards(memberId, type, date, keyword, cursor, size));
    }

    @GetMapping("/folders")
    @Operation(summary="마음카드 보관함 폴더 조회")
    public ApiResponse<CursorPageResponse<CardFolderResponse>> getFolders(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId,
            @Parameter(description = "조회함 유형", example = "RECEIVED") @RequestParam(defaultValue = "RECEIVED") CardBoxType type,
            @Parameter(description = "다음 페이지 조회용 커서", example = "MjAyNi0wOC0xNFQxMzowMDowMHwx")
            @RequestParam(required = false) String cursor,
            @Parameter(description = "페이지 크기", example = "20") @RequestParam(defaultValue = "20") int size
    ){
        return ApiResponse.ok("마음카드 폴더를 조회했습니다.", cardService.getFolders(memberId, type, cursor, size));
    }

    @GetMapping("/calendar")
    @Operation(summary="마음카드 보관함 월별 캘린더 요약 조회")
    public ApiResponse<CardCalendarResponse> getCalendar(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId,
            @Parameter(description = "조회함 유형", example = "RECEIVED") @RequestParam(defaultValue = "RECEIVED") CardBoxType type,
            @Parameter(description = "조회 연도", example = "2026") @RequestParam int year,
            @Parameter(description = "조회 월", example = "8") @RequestParam int month
    ){
        return ApiResponse.ok("마음카드 캘린더를 조회했습니다.", cardService.getCalendar(memberId, type, year, month));
    }

    @GetMapping("/{cardId}")
    @Operation(summary="특정 마음카드 조회")
    public ApiResponse<CardStorageResponse> getCard(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId,
            @Parameter(description = "조회할 마음카드 ID", example = "1") @PathVariable Long cardId
    ){
        return ApiResponse.ok("마음카드를 조회했습니다.", cardService.getCard(memberId, cardId));
    }

    @PostMapping("/{cardId}/image/complete")
    @Operation(summary="마음카드 이미지 업로드 완료")
    public ApiResponse<CardListResponse> completeImageUpload(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId,
            @Parameter(description = "이미지를 연결할 마음카드 ID", example = "1") @PathVariable Long cardId,
            @Valid @RequestBody CardImageUploadCompleteRequest request
    ){
        return ApiResponse.ok("이미지 업로드를 완료했습니다.", cardService.completeImageUpload(memberId, cardId, request));
    }

    @DeleteMapping("/{cardId}")
    @Operation(summary="내가 작성한 마음카드 삭제")
    public ApiResponse<Void> deleteWrittenCard(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId,
            @Parameter(description = "삭제할 마음카드 ID", example = "1") @PathVariable Long cardId
    ){
        cardService.deleteWrittenCard(memberId, cardId);
        return ApiResponse.ok("마음카드를 삭제했습니다.", null);
    }

    @PostMapping("/create")
    @Operation(summary="마음카드 생성", description = """
            로그인한 사용자가 보내는 사람이 되어 마음카드를 생성합니다.
            - 받는 사람(receiverId)과 봉투 디자인(designType)은 필수입니다(COMMON_002).
            - 본인에게는 보낼 수 없습니다(CARD_005).
            - 존재하지 않는 회원에게는 보낼 수 없습니다(CARD_006).
            - imageKey는 본인이 발급받은 presigned URL로 업로드를 마친 이미지만 사용할 수 있습니다.
            """)
    public ApiResponse<CardResponse> createCard(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId,
            @Valid @RequestBody CardCreateRequest request
    ){
        return ApiResponse.created("마음카드를 생성했습니다.", cardService.createCard(memberId, request));
    }
}
