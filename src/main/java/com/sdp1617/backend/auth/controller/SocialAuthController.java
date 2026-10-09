package com.sdp1617.backend.auth.controller;

import com.sdp1617.backend.auth.dto.SocialAuthResponse;
import com.sdp1617.backend.auth.dto.SocialLoginRequest;
import com.sdp1617.backend.auth.dto.SocialSignUpCompleteRequest;
import com.sdp1617.backend.auth.dto.TokenResponse;
import com.sdp1617.backend.auth.service.SocialAuthService;
import com.sdp1617.backend.global.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth/social")
@Tag(name = "소셜 인증", description = "카카오/구글/네이버 소셜 가입 및 로그인 API")
public class SocialAuthController {

    private final SocialAuthService socialAuthService;

    @Operation(summary = "소셜 로그인", description = """
            카카오/구글/네이버 계정으로 로그인합니다.
            - 이미 가입된 계정이면 즉시 `tokens`를 반환합니다.
            - 처음 로그인하는 계정이면 `signupToken`을 반환합니다. 이 토큰으로 `/signup/complete`를 호출해 아이디/약관 동의를 받아야 가입이 완료됩니다. signupToken은 발급 후 15분간 유효합니다.
            - 동일한 이메일로 이미 다른 방식(이메일 또는 다른 소셜)으로 가입된 계정이 있으면 신규 가입을 막습니다.
            """)
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "기존/신규 회원 공통 (data 형태로 구분)",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = SocialAuthResponse.class),
                            examples = {
                            @ExampleObject(name = "기존 회원", value = """
                            {
                              "success": true,
                              "code": "200",
                              "message": "소셜 인증에 성공하였습니다.",
                              "data": {
                                "isNewUser": false,
                                "tokens": {
                                  "accessToken": "eyJhbGciOiJIUzM4NCJ9...",
                                  "refreshToken": "eyJhbGciOiJIUzM4NCJ9..."
                                },
                                "signupToken": null,
                                "email": null
                              }
                            }
                            """),
                            @ExampleObject(name = "신규 회원", value = """
                            {
                              "success": true,
                              "code": "200",
                              "message": "소셜 인증에 성공하였습니다.",
                              "data": {
                                "isNewUser": true,
                                "tokens": null,
                                "signupToken": "e7a4e358-2b9b-40f2-ad19-cf85641806f9",
                                "email": "test@gmail.com"
                              }
                            }
                            """)
                    })),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "이미 다른 방식으로 가입된 이메일",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {
                              "success": false,
                              "code": "AUTH_012",
                              "message": "이미 다른 방식으로 가입된 이메일입니다.",
                              "data": null
                            }
                            """))),
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
    @PostMapping("/login")
    public ApiResponse<SocialAuthResponse> login(@Valid @RequestBody SocialLoginRequest request) {
        SocialAuthResponse response = socialAuthService.login(request.provider(), request.token());
        return ApiResponse.ok("소셜 인증에 성공하였습니다.", response);
    }

    @Operation(summary = "소셜 회원가입 완료", description = """
            `/login`에서 받은 signupToken으로 아이디/약관 동의를 입력받아 신규 소셜 회원가입을 완료합니다.
            - signupToken은 1회용이며, 사용 시점에 만료되었으면 다시 소셜 로그인부터 시작해야 합니다.
            """)
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "가입 완료",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = TokenResponse.class),
                            examples = @ExampleObject(value = """
                            {
                              "success": true,
                              "code": "200",
                              "message": "회원가입이 완료되었습니다.",
                              "data": {
                                "accessToken": "eyJhbGciOiJIUzM4NCJ9...",
                                "refreshToken": "eyJhbGciOiJIUzM4NCJ9..."
                              }
                            }
                            """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "아이디/이메일 중복",
                    content = @Content(mediaType = "application/json", examples = {
                            @ExampleObject(name = "아이디 중복", value = """
                                    {
                                      "success": false,
                                      "code": "AUTH_007",
                                      "message": "이미 사용 중인 아이디입니다.",
                                      "data": null
                                    }
                                    """),
                            @ExampleObject(name = "이미 다른 방식으로 가입된 이메일", value = """
                                    {
                                      "success": false,
                                      "code": "AUTH_012",
                                      "message": "이미 다른 방식으로 가입된 이메일입니다.",
                                      "data": null
                                    }
                                    """)
                    })),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "signupToken 만료 또는 유효하지 않음",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {
                              "success": false,
                              "code": "AUTH_011",
                              "message": "유효하지 않거나 만료된 링크입니다.",
                              "data": null
                            }
                            """)))
    })
    @PostMapping("/signup/complete")
    public ApiResponse<TokenResponse> completeSignUp(@Valid @RequestBody SocialSignUpCompleteRequest request) {
        TokenResponse response = socialAuthService.completeSignUp(
                request.signupToken(),
                request.nickname(),
                request.toConsent()
        );
        return ApiResponse.ok("회원가입이 완료되었습니다.", response);
    }
}
