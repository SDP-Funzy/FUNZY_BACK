package com.sdp1617.backend.letter.controller;

import com.sdp1617.backend.global.common.response.ApiResponse;
import com.sdp1617.backend.letter.dto.GiftSelectRequest;
import com.sdp1617.backend.letter.dto.LetterPageResponse;
import com.sdp1617.backend.letter.dto.LetterResponse;
import com.sdp1617.backend.letter.dto.LetterSendRequest;
import com.sdp1617.backend.letter.dto.ReceivedLetterResponse;
import com.sdp1617.backend.letter.dto.SentLetterResponse;
import com.sdp1617.backend.letter.dto.UnreadLetterCountResponse;
import com.sdp1617.backend.letter.entity.LetterSortType;
import com.sdp1617.backend.letter.service.LetterInboxService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/letters")
@RequiredArgsConstructor
@Tag(name = "편지 ② 보내기·편지함", description = """
        완료한 편지 보내기, 받은 편지함(= 받은 펀지팩 목록)·보낸 편지함, 편지 열기(= 펀지팩 열기)·삭제, 두들픽 선물 고르기.
        받는 사람에게 도착한 편지가 펀지팩이며, 보낸 사람과 같은 letterId를 씁니다.
        """)
public class LetterInboxController {

    private final LetterInboxService letterInboxService;

    @PostMapping("/{letterId}/send")
    @Operation(summary = "편지 보내기", description = """
            완료한 편지를 회원에게 보냅니다 (LW-822). 보내면 받는 사람의 받은 편지함에 도착하고, 더 이상 수정할 수 없습니다.
            - 받는 사람은 친구 목록 또는 회원 검색 결과의 memberId입니다. 친구가 아니어도 보낼 수 있습니다.
            - 완료하지 않은 편지는 LETTER_006, 이미 보낸 편지는 LETTER_009, 본인에게 보내면 LETTER_007,
              없거나 탈퇴한 회원이면 LETTER_008, 내가 쓴 편지가 아니면 LETTER_001입니다.
            """)
    public ApiResponse<LetterResponse> send(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId,
            @Parameter(description = "편지 ID", example = "1") @PathVariable Long letterId,
            @Valid @RequestBody LetterSendRequest request
    ) {
        return ApiResponse.ok("편지를 보냈습니다.", letterInboxService.send(memberId, letterId, request.recipientMemberId()));
    }

    @GetMapping("/received")
    @Operation(summary = "받은 편지함 (받은 펀지팩 목록)", description = """
            받은 편지 목록을 조회합니다 (LR-011/012). 받은 편지함에서 지운 편지는 나오지 않습니다.
            - senderName: 봉투의 보내는 사람 이름 또는 보낸 회원 닉네임에 포함된 편지만 (대소문자 무시)
            - receivedFrom/receivedTo: 받은 날짜 범위 (시작일·종료일 포함)
            - 페이지 번호는 0부터, size는 기본 20, 최대 100입니다. read가 false면 미읽음입니다.
            - 날짜를 고르면 그날 받은 편지만: receivedFrom과 receivedTo에 같은 날짜를 넣습니다.
            - thumbnailImageUrls: 목록에서 카드가 겹친 썸네일을 그릴 카드 사진 (카드 순서대로 사진 있는 카드 최대 3장)
            """)
    public ApiResponse<LetterPageResponse<ReceivedLetterResponse>> getReceivedLetters(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId,
            @Parameter(description = "정렬 기준. LATEST(최신순) 또는 OLDEST(오래된순)", example = "LATEST")
            @RequestParam(defaultValue = "LATEST") LetterSortType sort,
            @Parameter(description = "보낸 사람 검색어 (부분 일치)", example = "티키")
            @RequestParam(required = false) String senderName,
            @Parameter(description = "받은 날짜 시작일 (포함)", example = "2026-08-01")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @RequestParam(required = false) LocalDate receivedFrom,
            @Parameter(description = "받은 날짜 종료일 (포함)", example = "2026-08-31")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @RequestParam(required = false) LocalDate receivedTo,
            @Parameter(description = "페이지 번호 (0부터 시작)", example = "0") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기 (기본 20, 최대 100)", example = "20") @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.ok("받은 편지 목록을 조회했습니다.", letterInboxService.getReceivedLetters(
                memberId, sort, senderName, receivedFrom, receivedTo, page, size));
    }

    @GetMapping("/sent")
    @Operation(summary = "보낸 편지함", description = """
            내가 보낸 편지 목록을 최근에 보낸 순으로 조회합니다 (LW-840). read로 받는 사람이 읽었는지 알 수 있습니다.
            - 페이지 번호는 0부터, size는 기본 20, 최대 100입니다.
            - thumbnailImageUrls: 목록 썸네일용 카드 사진 (카드 순서대로 사진 있는 카드 최대 3장)
            """)
    public ApiResponse<LetterPageResponse<SentLetterResponse>> getSentLetters(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId,
            @Parameter(description = "페이지 번호 (0부터 시작)", example = "0") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기 (기본 20, 최대 100)", example = "20") @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.ok("보낸 편지 목록을 조회했습니다.", letterInboxService.getSentLetters(memberId, page, size));
    }

    @GetMapping("/received/unread-count")
    @Operation(summary = "미읽음 편지 수", description = """
            받은 편지함에서 아직 열지 않은 편지 수를 조회합니다 (LB-221). 홈·편지함의 미읽음 뱃지에 씁니다.
            - 받는 사람이 편지를 처음 열면(편지 열기 API) 읽음이 되어 개수에서 빠집니다.
            - 받은 편지함에서 지운 편지는 세지 않습니다.
            """)
    public ApiResponse<UnreadLetterCountResponse> getUnreadCount(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId
    ) {
        return ApiResponse.ok("미읽음 편지 수를 조회했습니다.", letterInboxService.getUnreadCount(memberId));
    }

    @GetMapping("/{letterId}")
    @Operation(summary = "편지 열기 (펀지팩 열기)", description = """
            편지를 마음카드(순서대로)·두들픽과 함께 조회합니다. 받는 사람에게는 펀지팩 열기(LR-020)이고, 카드를 넘겨보는 화면과 펀지팩 전체 저장(LR-511)에 이 응답을 씁니다.
            - 응답의 cards[].cardId로 마음카드 반응(이모지·문구 코멘트·콕)을, doodlePick.giftItems[].giftItemId로 선물 이모지·선물 고르기를 호출합니다.
            - 보낸 사람은 작성 중이든 보낸 뒤든 언제나 볼 수 있습니다 (LW-013 카드 목록 화면).
            - 받는 사람은 받은 편지함에서 지우지 않은 동안 볼 수 있고, 처음 열면 읽음(readAt)으로 표시됩니다 (LR-020).
            - 그 외에는 LETTER_001입니다.
            """)
    public ApiResponse<LetterResponse> getLetter(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId,
            @Parameter(description = "편지 ID", example = "1") @PathVariable Long letterId
    ) {
        return ApiResponse.ok("편지를 조회했습니다.", letterInboxService.getLetter(memberId, letterId));
    }

    @PutMapping("/{letterId}/gift")
    @Operation(summary = "두들픽 선물 고르기", description = """
            받은 편지의 두들픽 선물 후보 중 하나를 고릅니다 (LR-022). 다시 고르면 바뀌고, giftItemId를 비우면 선택을 취소합니다.
            - 편지에 없는 선물 후보면 LETTER_010, 내가 받은 편지가 아니면 COMMON_001입니다.
            """)
    public ApiResponse<LetterResponse> selectGift(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId,
            @Parameter(description = "편지 ID", example = "1") @PathVariable Long letterId,
            @RequestBody GiftSelectRequest request
    ) {
        return ApiResponse.ok("선물을 골랐습니다.", letterInboxService.selectGift(memberId, letterId, request.giftItemId()));
    }

    @DeleteMapping("/{letterId}")
    @Operation(summary = "편지 삭제 (보낸 사람: 작성 취소 / 받는 사람: 펀지팩 삭제)", description = """
            - 보낸 사람: 아직 보내지 않은 편지를 카드·두들픽과 함께 삭제합니다 (작성 취소). 보낸 편지는 삭제할 수 없습니다(LETTER_003).
            - 받는 사람: 받은 편지함에서 지웁니다 (LR-512). 복구할 수 없고, 이 편지에 남긴 내 반응(콕으로 담은 아카이브, 이모지, 문구 코멘트, 선물 이모지, 리액션·댓글·찜)와 이 편지로 받은 알림(편지 도착)도 함께 지워집니다. 보낸 사람의 보낸 편지함과 보낸 사람이 받은 반응 알림은 남습니다.
            """)
    public ApiResponse<Void> deleteOrHide(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId,
            @Parameter(description = "편지 ID", example = "1") @PathVariable Long letterId
    ) {
        letterInboxService.deleteOrHide(memberId, letterId);
        return ApiResponse.ok("편지를 삭제했습니다.", null);
    }
}
