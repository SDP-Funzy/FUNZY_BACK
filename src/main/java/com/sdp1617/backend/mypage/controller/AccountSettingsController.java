package com.sdp1617.backend.mypage.controller;

import com.sdp1617.backend.auth.entity.AuthProvider;
import com.sdp1617.backend.mypage.dto.ConnectedAccountResponse;
import com.sdp1617.backend.mypage.dto.LogoutRequest;
import com.sdp1617.backend.mypage.dto.PasswordChangeRequest;
import com.sdp1617.backend.mypage.dto.SocialConnectionAddRequest;
import com.sdp1617.backend.mypage.service.AccountSettingsService;
import com.sdp1617.backend.global.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
@Tag(name = "마이페이지 - 계정 설정", description = "비밀번호 변경, 연결 계정 조회, 로그아웃, 회원 탈퇴 API")
public class AccountSettingsController {

    private final AccountSettingsService accountSettingsService;

    @Operation(summary = "연결 계정 조회", description = """
            현재 연결된 소셜 provider 목록과 비밀번호 보유 여부를 조회합니다.
            - 회원 1명이 로컬 비밀번호 + 소셜 여러 개를 동시에 가질 수 있습니다.
            - hasPassword가 false면 비밀번호 변경 메뉴를 노출하지 않아야 합니다.
            """)
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ConnectedAccountResponse.class),
                            examples = {
                            @ExampleObject(name = "이메일 가입 + 소셜 연결 없음", value = """
                                    {
                                      "success": true,
                                      "code": "200",
                                      "message": "연결 계정을 조회했습니다.",
                                      "data": { "connectedProviders": [], "hasPassword": true }
                                    }
                                    """),
                            @ExampleObject(name = "소셜 전용 계정 (연결 2개)", value = """
                                    {
                                      "success": true,
                                      "code": "200",
                                      "message": "연결 계정을 조회했습니다.",
                                      "data": { "connectedProviders": ["KAKAO", "GOOGLE"], "hasPassword": false }
                                    }
                                    """)
                    }))
    })
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
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "연결 성공",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {
                              "success": true,
                              "code": "200",
                              "message": "소셜 계정이 연결되었습니다.",
                              "data": null
                            }
                            """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "이미 연결됨 (본인 또는 다른 계정)",
                    content = @Content(mediaType = "application/json", examples = {
                            @ExampleObject(name = "본인 계정에 이미 연결된 provider", value = """
                                    {
                                      "success": false,
                                      "code": "AUTH_018",
                                      "message": "이미 연결된 소셜 계정입니다.",
                                      "data": null
                                    }
                                    """),
                            @ExampleObject(name = "다른 계정에 이미 연결된 소셜 계정", value = """
                                    {
                                      "success": false,
                                      "code": "AUTH_017",
                                      "message": "이미 다른 계정에 연결된 소셜 계정입니다.",
                                      "data": null
                                    }
                                    """)
                    })),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "소셜 인증 실패 (유효하지 않거나 만료된 토큰)",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {
                              "success": false,
                              "code": "AUTH_013",
                              "message": "소셜 인증에 실패했습니다.",
                              "data": null
                            }
                            """)))
    })
    @PostMapping("/connections")
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
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "해제 성공",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {
                              "success": true,
                              "code": "200",
                              "message": "소셜 계정 연결이 해제되었습니다.",
                              "data": null
                            }
                            """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "마지막 남은 로그인 수단",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {
                              "success": false,
                              "code": "AUTH_020",
                              "message": "마지막 남은 로그인 수단은 연결 해제할 수 없습니다.",
                              "data": null
                            }
                            """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "연결되지 않은 소셜 계정",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {
                              "success": false,
                              "code": "AUTH_019",
                              "message": "연결되지 않은 소셜 계정입니다.",
                              "data": null
                            }
                            """)))
    })
    @DeleteMapping("/connections/{provider}")
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
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "변경 성공",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {
                              "success": true,
                              "code": "200",
                              "message": "비밀번호가 변경되었습니다.",
                              "data": null
                            }
                            """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "소셜 전용 계정 / 현재 비밀번호 불일치 / 새 비밀번호 확인 불일치 / 현재와 같은 새 비밀번호",
                    content = @Content(mediaType = "application/json", examples = {
                            @ExampleObject(name = "소셜 전용 계정", value = """
                                    {
                                      "success": false,
                                      "code": "AUTH_014",
                                      "message": "소셜 전용 계정은 비밀번호를 변경할 수 없습니다.",
                                      "data": null
                                    }
                                    """),
                            @ExampleObject(name = "현재 비밀번호 불일치", value = """
                                    {
                                      "success": false,
                                      "code": "AUTH_015",
                                      "message": "현재 비밀번호가 일치하지 않습니다.",
                                      "data": null
                                    }
                                    """),
                            @ExampleObject(name = "새 비밀번호 확인 불일치", value = """
                                    {
                                      "success": false,
                                      "code": "AUTH_008",
                                      "message": "비밀번호가 일치하지 않습니다.",
                                      "data": null
                                    }
                                    """),
                            @ExampleObject(name = "현재 비밀번호와 같은 새 비밀번호", value = """
                                    {
                                      "success": false,
                                      "code": "AUTH_028",
                                      "message": "현재 비밀번호와 다른 비밀번호를 입력해주세요.",
                                      "data": null
                                    }
                                    """)
                    }))
    })
    @PatchMapping("/password")
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
            """)
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "로그아웃 성공",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {
                              "success": true,
                              "code": "200",
                              "message": "로그아웃되었습니다.",
                              "data": null
                            }
                            """)))
    })
    @PostMapping("/logout")
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
            - 현재는 회원 계정만 삭제하며, 아카이브 등 연관 도메인 데이터의 삭제 범위는 기획 확정 후 별도 처리됩니다.
            - 탈퇴 성공 시 로그인되어 있던 모든 기기의 세션이 종료됩니다.
            """)
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "탈퇴 성공",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {
                              "success": true,
                              "code": "200",
                              "message": "회원 탈퇴가 완료되었습니다.",
                              "data": null
                            }
                            """)))
    })
    @DeleteMapping
    public ApiResponse<Void> withdraw(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId
    ) {
        accountSettingsService.withdraw(memberId);
        return ApiResponse.ok("회원 탈퇴가 완료되었습니다.", null);
    }
}
