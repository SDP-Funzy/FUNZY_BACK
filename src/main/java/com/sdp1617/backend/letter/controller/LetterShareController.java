package com.sdp1617.backend.letter.controller;

import com.sdp1617.backend.global.common.response.ApiResponse;
import com.sdp1617.backend.global.error.CustomException;
import com.sdp1617.backend.global.error.ErrorCode;
import com.sdp1617.backend.global.security.JwtAuthenticationFilter;
import com.sdp1617.backend.letter.dto.LetterResponse;
import com.sdp1617.backend.letter.dto.ShareLinkResponse;
import com.sdp1617.backend.letter.dto.SharedLetterResponse;
import com.sdp1617.backend.letter.service.LetterShareService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/letters")
@RequiredArgsConstructor
@Tag(name = "편지 ② 보내기·편지함")
public class LetterShareController {

    private final LetterShareService letterShareService;

    @PostMapping("/{letterId}/share-link")
    @Operation(summary = "공유 링크 만들기 (링크로 보내기)", description = """
            편지를 링크로 보냅니다 (LW-830, #87). 앱은 응답의 token으로 공유할 주소를 만들어 카카오톡 등으로 전달합니다.
            - 완료한 편지는 이때 **보낸 편지가 되어 더 이상 수정할 수 없고**, 받는 사람은 링크를 받은 회원이 '받기'를 하면 정해집니다.
            - 회원에게 직접 보낸 편지도 링크를 만들 수 있습니다 (받는 사람은 그대로).
            - 링크는 30일 동안 유효합니다. 다시 호출하면 새 링크가 발급되고 **이전 링크는 무효**가 됩니다.
            - 작성 중인(완료 전) 편지는 LETTER_006, 내가 쓴 편지가 아니면 LETTER_001입니다.
            """)
    public ApiResponse<ShareLinkResponse> issue(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId,
            @Parameter(description = "편지 ID", example = "1") @PathVariable Long letterId
    ) {
        return ApiResponse.ok("공유 링크를 만들었습니다.", letterShareService.issue(memberId, letterId));
    }

    @DeleteMapping("/{letterId}/share-link")
    @Operation(summary = "공유 링크 취소", description = """
            공유 링크를 무효로 만듭니다. 이미 받은 사람은 받은 편지함에서 계속 볼 수 있습니다. 필요하면 다시 만들 수 있습니다.
            """)
    public ApiResponse<Void> revoke(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId,
            @Parameter(description = "편지 ID", example = "1") @PathVariable Long letterId
    ) {
        letterShareService.revoke(memberId, letterId);
        return ApiResponse.ok("공유 링크를 취소했습니다.", null);
    }

    @GetMapping("/shared/{token}")
    @SecurityRequirements
    @Operation(summary = "공유 링크로 편지 보기 (로그인 불필요)", description = """
            링크로 받은 편지를 봅니다 (LR-030/031). **로그인 없이** 누구나 읽기 전용으로 볼 수 있습니다.
            - 로그인한 상태라면 access token을 함께 보내 주세요. viewer와 receivable로 화면을 나눕니다.
              - ANONYMOUS: 내용만 보여주고, 반응·선물 고르기를 누르면 로그인 유도 (LR-032)
              - OTHER + receivable=true: 로그인한 회원이 처음 연 것 → 바로 '받기'(POST .../receive) 호출
              - OTHER + receivable=false: 이미 다른 사람이 받은 편지 → 읽기만
              - RECIPIENT: 받는 사람 → 반응·선물 고르기 가능 / SENDER: 보낸 사람 미리보기
            - 보낸 사람·받는 사람이 아니면 받는 사람 정보·읽은 시각·고른 선물(letter.recipient·readAt·selectedGiftItemId)은 null로 가립니다.
            - access token을 보냈는데 만료·무효면 401(AUTH_004 만료 → 재발급 후 다시 요청, AUTH_003·AUTH_027 → 로그인)입니다.
              토큰을 보내지 않으면 로그인하지 않은 사람으로 봅니다.
            - 이 요청은 받는 사람을 정하지 않습니다 (링크 미리보기 등이 실수로 받지 않도록).
            - 없는·취소된·만료된 링크는 LETTER_011입니다.
            """)
    public ApiResponse<SharedLetterResponse> view(
            @Parameter(description = "공유 링크 토큰") @PathVariable String token,
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId,
            HttpServletRequest request
    ) {
        // 로그인 없이도 열 수 있는 API지만, access token을 보냈는데 만료·무효면 익명으로 넘기지 않고 401을 돌려준다 —
        // 그래야 앱이 토큰을 재발급해 받는 사람에게 가려지지 않은 편지를 보여줄 수 있다
        if (request.getAttribute(JwtAuthenticationFilter.ERROR_CODE_ATTRIBUTE) instanceof ErrorCode tokenError) {
            throw new CustomException(tokenError);
        }
        return ApiResponse.ok("편지를 조회했습니다.", letterShareService.view(token, memberId));
    }

    @PostMapping("/shared/{token}/receive")
    @Operation(summary = "공유 링크로 편지 받기", description = """
            로그인한 회원을 이 편지의 받는 사람으로 정합니다 (#114). 받은 편지함에 들어가고, 반응·선물 고르기를 할 수 있습니다.
            - 처음 받은 1명만 받는 사람이 됩니다(동시에 받아도 1명). 이미 다른 회원이 받았으면 LETTER_012, 같은 회원이 다시 받으면 그대로 성공합니다.
            - 받은 편지함에서 지웠던 받는 사람이 다시 받으면 받은 편지함에 되돌립니다 (지울 때 정리된 반응은 돌아오지 않음).
            - 받은 편지함의 받은 날짜는 받기를 누른 시각입니다 (링크를 만든 날이 아님).
            - 보낸 사람 본인은 받을 수 없습니다(LETTER_013). 없는·취소된·만료된 링크는 LETTER_011입니다.
            """)
    public ApiResponse<LetterResponse> receive(
            @Parameter(description = "공유 링크 토큰") @PathVariable String token,
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId
    ) {
        return ApiResponse.ok("편지를 받았습니다.", letterShareService.receive(token, memberId));
    }
}
