package com.sdp1617.backend.auth.controller;

import com.sdp1617.backend.global.error.ErrorCode;
import com.sdp1617.backend.global.error.ApiErrorCodes;
import com.sdp1617.backend.auth.dto.TokenResponse;
import com.sdp1617.backend.auth.dto.TokenReissueRequest;
import com.sdp1617.backend.auth.service.TokenService;
import com.sdp1617.backend.global.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import com.sdp1617.backend.global.config.ApiTags;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = ApiTags.AUTH_TOKEN)
public class TokenController {

    private final TokenService tokenService;

    @Operation(summary = "토큰 재발급", description = """
            refresh token으로 새 access token과 새 refresh token을 발급합니다 (refresh token rotation).
            - 응답의 refreshToken을 저장해서 다음 재발급부터 사용해야 합니다. 사용한 refresh token은 즉시 폐기됩니다.
            - 재발급할 때마다 refresh token 만료가 다시 30일로 시작되므로, 앱을 쓰는 동안은 로그인이 유지되고 30일 동안 사용하지 않으면 로그아웃됩니다.
            - 동시에 여러 요청에서 같은 refresh token으로 재발급해도, 교체된 지 30초 안의 토큰이면 같은 세션의 최신 토큰을 돌려줍니다.
            - 그보다 오래된(이미 교체된) refresh token을 다시 사용하면 탈취로 판단해 그 로그인 세션(해당 기기)을 종료합니다(AUTH_005). 다른 기기의 로그인은 유지됩니다.
            - 로그아웃 등으로 폐기된 세션이면 재발급에 실패합니다(AUTH_005). 탈퇴한 회원이면 AUTH_003입니다.
            - 비밀번호 변경·재설정, 잠금 해제 전에 로그인한 세션이면 재발급에 실패합니다(AUTH_027). 새 비밀번호로 다시 로그인해야 합니다.
            - 재발급한 토큰은 처음 로그인한 세션을 이어가는 것이라, 비밀번호가 바뀌면 재발급받은 토큰도 함께 무효가 됩니다.
            """)
    @PostMapping("/api/auth/token/reissue")
    @ApiErrorCodes({ErrorCode.COMMON_002, ErrorCode.AUTH_003, ErrorCode.AUTH_004, ErrorCode.AUTH_005, ErrorCode.AUTH_027})
    public ApiResponse<TokenResponse> reissue(@Valid @RequestBody TokenReissueRequest request) {
        TokenResponse response = tokenService.reissue(request.refreshToken());
        return ApiResponse.ok("토큰이 재발급되었습니다.", response);
    }
}
