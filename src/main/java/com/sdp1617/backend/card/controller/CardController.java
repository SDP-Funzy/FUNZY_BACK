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
@Tag(name = "마음카드 작성·보관함", description = "마음카드 작성, 카드 이미지 업로드, 보낸/받은 마음카드 보관함 조회 API")
public class CardController {

    private final CardService cardService;

    @PostMapping("/images/presigned-url")
    @Operation(summary="마음카드 이미지 업로드용 Presigned URL 발급", description = """
            카드에 첨부할 이미지를 S3에 직접 업로드할 수 있는 presigned URL을 발급합니다.
            - 지원 형식: image/jpeg, image/png, image/webp (그 외 CARD_003)
            - 발급받은 uploadUrl로 PUT 업로드한 뒤, imageKey를 카드 생성 요청에 넣거나 이미지 업로드 완료 API로 카드에 연결합니다.
            - imageKey는 본인에게 발급된 것만 사용할 수 있습니다.
            """)
    public ApiResponse<CardImagePresignedUrlResponse> issueImagePresignedUrl(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId,
            @Valid @RequestBody CardImagePresignedUrlRequest request
    ){
        return ApiResponse.ok("이미지 업로드 URL을 발급했습니다.", cardService.issueImagePresignedUrl(memberId, request));
    }

    @GetMapping
    @Operation(summary="마음카드 보관함 목록 조회", description = """
            보낸(SENT) 또는 받은(RECEIVED) 마음카드를 최신순으로 조회합니다.
            - date를 주면 그날 작성된 카드만, keyword를 주면 제목·내용·보낸 사람·받은 사람 닉네임에 포함된 카드만 조회합니다.
            - 커서 기반 페이지네이션: 응답의 nextCursor를 다음 요청의 cursor로 보내고, hasNext가 false면 마지막 페이지입니다.
            - size는 기본 20, 최대 50입니다. 잘못된 cursor는 COMMON_002입니다.
            """)
    public ApiResponse<CursorPageResponse<CardStorageResponse>> getCards(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId,
            @Parameter(description = "조회함 유형", example = "RECEIVED") @RequestParam(defaultValue = "RECEIVED") CardBoxType type,
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
    @Operation(summary="마음카드 보관함 폴더 조회", description = """
            주고받은 상대방별로 묶은 폴더 목록을 최근 카드 순으로 조회합니다.
            - 폴더마다 상대 회원 ID·닉네임, 카드 수, 최근 카드 이미지, 최근 카드 작성 시각을 반환합니다.
            - 페이지네이션 방식은 보관함 목록 조회와 같습니다(size 기본 20, 최대 50).
            """)
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
    @Operation(summary="마음카드 보관함 월별 캘린더 요약 조회", description = """
            해당 월에 카드가 있는 날짜와, 날짜별 카드 이미지 URL 목록을 조회합니다.
            - 카드가 없는 날짜는 days에 포함되지 않습니다. 이미지가 없는 카드는 이미지 목록에서 제외됩니다.
            """)
    public ApiResponse<CardCalendarResponse> getCalendar(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId,
            @Parameter(description = "조회함 유형", example = "RECEIVED") @RequestParam(defaultValue = "RECEIVED") CardBoxType type,
            @Parameter(description = "조회 연도", example = "2026") @RequestParam int year,
            @Parameter(description = "조회 월", example = "8") @RequestParam int month
    ){
        return ApiResponse.ok("마음카드 캘린더를 조회했습니다.", cardService.getCalendar(memberId, type, year, month));
    }

    @GetMapping("/{cardId}")
    @Operation(summary="특정 마음카드 조회", description = """
            내가 보냈거나 받은 마음카드 1장을 조회합니다.
            - 존재하지 않거나 내가 보낸/받은 카드가 아니면 CARD_001입니다.
            """)
    public ApiResponse<CardStorageResponse> getCard(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId,
            @Parameter(description = "조회할 마음카드 ID", example = "1") @PathVariable Long cardId
    ){
        return ApiResponse.ok("마음카드를 조회했습니다.", cardService.getCard(memberId, cardId));
    }

    @PostMapping("/{cardId}/image/complete")
    @Operation(summary="마음카드 이미지 업로드 완료", description = """
            S3 업로드를 마친 이미지를 내가 작성한 카드에 연결합니다. 기존 이미지가 있으면 교체됩니다.
            - 업로드가 끝나지 않았으면 CARD_002, 지원하지 않는 형식이면 CARD_003, 5MB 초과면 CARD_004입니다.
            - 본인에게 발급된 imageKey가 아니면 COMMON_004, 내가 작성한 카드가 아니면 CARD_001입니다.
            """)
    public ApiResponse<CardListResponse> completeImageUpload(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId,
            @Parameter(description = "이미지를 연결할 마음카드 ID", example = "1") @PathVariable Long cardId,
            @Valid @RequestBody CardImageUploadCompleteRequest request
    ){
        return ApiResponse.ok("이미지 업로드를 완료했습니다.", cardService.completeImageUpload(memberId, cardId, request));
    }

    @DeleteMapping("/{cardId}")
    @Operation(summary="내가 작성한 마음카드 삭제", description = """
            내가 작성한 마음카드를 삭제합니다. 받은 카드는 삭제할 수 없습니다.
            - 존재하지 않거나 내가 작성한 카드가 아니면 CARD_001입니다.
            """)
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
