package com.sdp1617.backend.letter.controller;

import com.sdp1617.backend.global.common.response.ApiResponse;
import com.sdp1617.backend.letter.dto.ReceivedLetterListResponse;
import com.sdp1617.backend.letter.entity.LetterSortType;
import com.sdp1617.backend.letter.service.ReceivedLetterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
@Tag(name = "받은 편지", description = "받은 편지 목록 조회, 필터, 정렬 API")
public class ReceivedLetterController {

    private final ReceivedLetterService receivedLetterService;

    @GetMapping("/api/letters/received")
    @Operation(
            summary = "받은 편지 목록 조회",
            description = """
                    로그인한 사용자가 받은 편지 목록을 조회합니다.
                    - 보낸 사람 이름(대소문자 무시, 부분 일치)과 받은 날짜 범위로 거를 수 있습니다. 날짜 범위는 시작일·종료일을 모두 포함합니다.
                    - 페이지 번호는 0부터 시작하며, size는 기본 20, 최대 100입니다.
                    """
    )
    public ApiResponse<ReceivedLetterListResponse> getReceivedLetters(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId,
            @Parameter(description = "정렬 기준. LATEST(최신순) 또는 OLDEST(오래된순)", example = "LATEST")
            @RequestParam(defaultValue = "LATEST") LetterSortType sort,
            @Parameter(description = "보낸 사람 이름 검색어 (부분 일치)", example = "은우")
            @RequestParam(required = false) String senderName,
            @Parameter(description = "받은 날짜 시작일 (포함)", example = "2026-08-01")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            @RequestParam(required = false) LocalDate receivedFrom,
            @Parameter(description = "받은 날짜 종료일 (포함)", example = "2026-08-31")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            @RequestParam(required = false) LocalDate receivedTo,
            @Parameter(description = "페이지 번호 (0부터 시작)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기 (기본 20, 최대 100)", example = "20")
            @RequestParam(defaultValue = "20") int size
    ) {
        ReceivedLetterListResponse response = receivedLetterService.getReceivedLetters(
                memberId,
                sort,
                senderName,
                receivedFrom,
                receivedTo,
                page,
                size
        );
        return ApiResponse.ok("받은 편지 목록을 조회했습니다.", response);
    }
}
