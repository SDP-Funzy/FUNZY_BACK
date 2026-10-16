package com.sdp1617.backend.mypage.controller;

import com.sdp1617.backend.global.error.ErrorCode;
import com.sdp1617.backend.global.error.ApiErrorCodes;
import com.sdp1617.backend.auth.entity.AuthProvider;
import com.sdp1617.backend.mypage.dto.ConnectedAccountResponse;
import com.sdp1617.backend.mypage.dto.LogoutRequest;
import com.sdp1617.backend.mypage.dto.PasswordChangeRequest;
import com.sdp1617.backend.mypage.dto.SocialConnectionAddRequest;
import com.sdp1617.backend.mypage.service.AccountSettingsService;
import com.sdp1617.backend.global.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import com.sdp1617.backend.global.config.ApiTags;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/mypage/account")
@Tag(name = ApiTags.MYPAGE_ACCOUNT)
public class AccountSettingsController {

    private final AccountSettingsService accountSettingsService;

    @Operation(summary = "연결 계정 조회", description = """
            현재 연결된 소셜 provider 목록과 비밀번호 보유 여부를 조회합니다.
            - 회원 1명이 로컬 비밀번호 + 소셜 여러 개를 동시에 가질 수 있습니다.
            - hasPassword가 false면 비밀번호 변경 메뉴를 노출하지 않아야 합니다.
            """)
    @GetMapping("/connected")
    public ApiResponse<ConnectedAccountResponse> getConnectedAccount(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId
    ) {
        return ApiResponse.ok("연결 계정을 조회했습니다.", accountSettingsService.getConnectedAccount(memberId));
    }

    @Operation(summary = "소셜 계정 연결 추가", description = """
            현재 로그인된 본인 계정에 새 소셜 provider를 추가로 연결합니다.
            - 연결 성공 시 이후 해당 provider로도 로그인할 수 있습니다.
            - 이메일이 같아도 자동으로 다른 계정과 병합하지 않으며, 항상 현재 로그인된 회원에 연결됩니다.
            """)
    @PostMapping("/connections")
    @ApiErrorCodes({ErrorCode.COMMON_002, ErrorCode.AUTH_013, ErrorCode.AUTH_017, ErrorCode.AUTH_018})
    public ApiResponse<Void> connectSocialAccount(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId,
            @Valid @RequestBody SocialConnectionAddRequest request
    ) {
        accountSettingsService.connectSocialAccount(memberId, request.provider(), request.token());
        return ApiResponse.ok("소셜 계정이 연결되었습니다.", null);
    }

    @Operation(summary = "소셜 계정 연결 해제", description = """
            현재 로그인된 본인 계정에서 지정한 provider의 소셜 연결을 해제합니다.
            - 비밀번호가 없고(소셜 전용 계정) 연결된 소셜이 이것 하나뿐이면 해제할 수 없습니다(로그인 수단 소실 방지).
            """)
    @DeleteMapping("/connections/{provider}")
    @ApiErrorCodes({ErrorCode.COMMON_002, ErrorCode.AUTH_019, ErrorCode.AUTH_020})
    public ApiResponse<Void> disconnectSocialAccount(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId,
            @Parameter(description = "해제할 소셜 provider", example = "KAKAO") @PathVariable AuthProvider provider
    ) {
        accountSettingsService.disconnectSocialAccount(memberId, provider);
        return ApiResponse.ok("소셜 계정 연결이 해제되었습니다.", null);
    }

    @Operation(summary = "비밀번호 변경", description = """
            현재 비밀번호 확인 후 새 비밀번호로 변경합니다.
            - 소셜 전용 계정(비밀번호 없음)은 변경할 수 없습니다.
            - 새 비밀번호가 현재 비밀번호와 같으면 변경할 수 없습니다(AUTH_028).
            - 변경 성공 시 이 기기를 포함해 로그인되어 있던 모든 기기의 세션이 종료됩니다.
              이미 발급된 access token도 바로 쓸 수 없게 되어(AUTH_027), 변경 후에는 새 비밀번호로 다시 로그인해야 합니다.
            """)
    @PatchMapping("/password")
    @ApiErrorCodes({ErrorCode.COMMON_002, ErrorCode.AUTH_008, ErrorCode.AUTH_014, ErrorCode.AUTH_015, ErrorCode.AUTH_028})
    public ApiResponse<Void> changePassword(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId,
            @Valid @RequestBody PasswordChangeRequest request
    ) {
        accountSettingsService.changePassword(
                memberId, request.currentPassword(), request.newPassword(), request.newPasswordConfirm());
        return ApiResponse.ok("비밀번호가 변경되었습니다.", null);
    }

    @Operation(summary = "로그아웃", description = """
            요청 바디로 전달한 refreshToken에 해당하는 세션만 종료합니다.
            - 다른 기기에서 로그인된 세션에는 영향을 주지 않습니다.
            - refreshToken이 내 토큰이 아니거나 형식이 잘못됐으면 AUTH_003, 만료됐으면 AUTH_004입니다. 이때 세션은 이미 끝난 것이므로 앱은 저장된 토큰을 지우고 로그아웃 처리하면 됩니다.
            """)
    @PostMapping("/logout")
    @ApiErrorCodes({ErrorCode.COMMON_002, ErrorCode.AUTH_003, ErrorCode.AUTH_004})
    public ApiResponse<Void> logout(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId,
            @Valid @RequestBody LogoutRequest request
    ) {
        accountSettingsService.logout(memberId, request.refreshToken());
        return ApiResponse.ok("로그아웃되었습니다.", null);
    }

    @Operation(summary = "회원 탈퇴", description = """
            계정을 삭제하고 서비스를 탈퇴합니다.
            - 삭제 후 데이터 복구가 불가능합니다.
            - 지워지는 것: 소셜 연결(같은 소셜 계정으로 다시 가입 가능), 친구 관계·친구 요청, 내 아카이브와 거기 달린 댓글, 내 알림,
              웹 푸시 구독, 보내지 않은 내 편지, 프로필 이미지. 받은 편지는 받은 편지함에서 빠지고, 내가 만든 공유 링크는 무효가 됩니다.
            - 남는 것: 주고받은 편지(상대의 편지함·보관함에서 보임)와 남의 카드에 단 댓글. 작성자는 "탈퇴한회원N"으로 보입니다.
            - 탈퇴 성공 시 로그인되어 있던 모든 기기의 세션이 종료됩니다.
            """)
    @DeleteMapping
    public ApiResponse<Void> withdraw(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId
    ) {
        accountSettingsService.withdraw(memberId);
        return ApiResponse.ok("회원 탈퇴가 완료되었습니다.", null);
    }
}
