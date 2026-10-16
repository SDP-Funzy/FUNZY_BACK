package com.sdp1617.backend.social.controller;

import com.sdp1617.backend.global.error.ErrorCode;
import com.sdp1617.backend.global.error.ApiErrorCodes;
import com.sdp1617.backend.global.common.response.ApiResponse;
import com.sdp1617.backend.social.dto.MemberProfileResponse;
import com.sdp1617.backend.social.service.MemberSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import com.sdp1617.backend.global.config.ApiTags;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/social/members")
@RequiredArgsConstructor
@Tag(name = ApiTags.FRIEND_SEARCH)
public class MemberSearchController {

    private final MemberSearchService memberSearchService;

    @GetMapping("/search")
    @Operation(summary = "아이디로 회원 검색", description = """
            아이디의 앞부분이 일치하는 회원을 친구 먼저, 최대 20명까지 조회합니다 (LW-820 받는 사람 선택).
            - 검색어는 2자 이상이어야 합니다(SOCIAL_008). 대소문자는 구분하지 않습니다.
            - 나 자신과 탈퇴한 회원은 나오지 않습니다.
            - 회원당 1분에 30번까지 검색할 수 있습니다(SOCIAL_009).
            - followStatus로 버튼 상태를 그립니다: NONE(친구 요청) / REQUESTED(요청함, followRequestId로 취소) /
              RECEIVED(상대가 요청함, followRequestId로 수락·거절) / FRIEND(친구)
            """)
    @ApiErrorCodes({ErrorCode.COMMON_002, ErrorCode.SOCIAL_008, ErrorCode.SOCIAL_009})
    public ApiResponse<List<MemberProfileResponse>> search(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId,
            @Parameter(description = "아이디 검색어 (앞부분, 2자 이상)", example = "tik") @RequestParam String nickname
    ) {
        return ApiResponse.ok("회원을 검색했습니다.", memberSearchService.searchByNickname(memberId, nickname));
    }

    @GetMapping("/{memberId}")
    @Operation(summary = "회원 프로필·친구 관계 상태 조회", description = """
            회원 ID로 아이디·프로필 사진과 나와의 관계를 조회합니다. 친구 아카이브 블러 화면의 버튼 상태에 씁니다.
            - 진입 경로: 회원 검색 결과의 memberId, 친구 요청 알림의 actorMemberId 등
            - followStatus: NONE(친구 요청 → POST /api/social/follow/requests) /
              REQUESTED(요청함 → 취소는 DELETE /api/social/follow/requests/{followRequestId}) /
              RECEIVED(상대가 요청함 → 수락·거절은 followRequestId, 내가 요청을 보내도 바로 친구가 됨) /
              FRIEND(친구 아카이브 GET /api/archive/friends/{memberId}/home) / SELF(나)
            - 탈퇴했거나 없는 회원이면 SOCIAL_001입니다.
            - 친구가 아닌 회원 조회는 회원당 1분에 60번까지입니다(SOCIAL_009). 나·친구 조회는 세지 않습니다.
            """)
    @ApiErrorCodes({ErrorCode.SOCIAL_001, ErrorCode.SOCIAL_009})
    public ApiResponse<MemberProfileResponse> getProfile(
            @Parameter(hidden = true) @AuthenticationPrincipal Long viewerId,
            @Parameter(description = "조회할 회원 ID", example = "2") @PathVariable Long memberId
    ) {
        return ApiResponse.ok("회원 프로필을 조회했습니다.", memberSearchService.getProfile(viewerId, memberId));
    }
}
