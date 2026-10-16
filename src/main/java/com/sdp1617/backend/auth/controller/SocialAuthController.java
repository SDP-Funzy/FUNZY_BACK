package com.sdp1617.backend.auth.controller;

import com.sdp1617.backend.global.error.ErrorCode;
import com.sdp1617.backend.global.error.ApiErrorCodes;
import com.sdp1617.backend.auth.dto.SocialAuthResponse;
import com.sdp1617.backend.auth.dto.SocialLoginRequest;
import com.sdp1617.backend.auth.dto.SocialSignUpCompleteRequest;
import com.sdp1617.backend.auth.dto.TokenResponse;
import com.sdp1617.backend.auth.service.SocialAuthService;
import com.sdp1617.backend.global.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import com.sdp1617.backend.global.config.ApiTags;
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
@Tag(name = ApiTags.AUTH_SOCIAL)
public class SocialAuthController {

    private final SocialAuthService socialAuthService;

    @Operation(summary = "소셜 로그인", description = """
            카카오/구글/네이버 계정으로 로그인합니다.
            - 이미 가입된 계정이면 즉시 `tokens`를 반환합니다.
            - 처음 로그인하는 계정이면 `signupToken`을 반환합니다. 이 토큰으로 `/signup/complete`를 호출해 아이디/약관 동의를 받아야 가입이 완료됩니다. signupToken은 발급 후 15분간 유효합니다.
            - 동일한 이메일로 이미 다른 방식(이메일 또는 다른 소셜)으로 가입된 계정이 있으면 신규 가입을 막습니다.
            """)
    @PostMapping("/login")
    @ApiErrorCodes({ErrorCode.COMMON_002, ErrorCode.AUTH_012, ErrorCode.AUTH_013})
    public ApiResponse<SocialAuthResponse> login(@Valid @RequestBody SocialLoginRequest request) {
        SocialAuthResponse response = socialAuthService.login(request.provider(), request.token());
        return ApiResponse.ok("소셜 인증에 성공하였습니다.", response);
    }

    @Operation(summary = "소셜 회원가입 완료", description = """
            `/login`에서 받은 signupToken으로 아이디/약관 동의를 입력받아 신규 소셜 회원가입을 완료합니다.
            - signupToken은 1회용이며, 사용 시점에 만료되었으면 다시 소셜 로그인부터 시작해야 합니다.
            """)
    @PostMapping("/signup/complete")
    @ApiErrorCodes({ErrorCode.COMMON_002, ErrorCode.AUTH_007, ErrorCode.AUTH_011, ErrorCode.AUTH_012})
    public ApiResponse<TokenResponse> completeSignUp(@Valid @RequestBody SocialSignUpCompleteRequest request) {
        TokenResponse response = socialAuthService.completeSignUp(
                request.signupToken(),
                request.nickname(),
                request.toConsent()
        );
        return ApiResponse.ok("회원가입이 완료되었습니다.", response);
    }
}
