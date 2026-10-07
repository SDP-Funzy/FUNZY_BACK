package com.sdp1617.backend.social.controller;

import com.sdp1617.backend.global.common.response.ApiResponse;
import com.sdp1617.backend.social.dto.MemberSearchResponse;
import com.sdp1617.backend.social.service.MemberSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/social/members")
@RequiredArgsConstructor
@Tag(name = "소셜 - 회원 검색", description = "닉네임(아이디)으로 회원 찾기 API")
public class MemberSearchController {

    private final MemberSearchService memberSearchService;

    @GetMapping("/search")
    @Operation(summary = "닉네임으로 회원 검색", description = """
            닉네임(아이디)의 앞부분이 일치하는 회원을 친구 먼저, 최대 20명까지 조회합니다 (LW-820 받는 사람 선택).
            - 검색어는 2자 이상이어야 합니다(SOCIAL_008). 대소문자는 구분하지 않습니다.
            - 나 자신과 탈퇴한 회원은 나오지 않습니다.
            - 회원당 1분에 30번까지 검색할 수 있습니다(SOCIAL_009).
            """)
    public ApiResponse<List<MemberSearchResponse>> search(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId,
            @Parameter(description = "닉네임 검색어 (앞부분, 2자 이상)", example = "tik") @RequestParam String nickname
    ) {
        return ApiResponse.ok("회원을 검색했습니다.", memberSearchService.searchByNickname(memberId, nickname));
    }
}
