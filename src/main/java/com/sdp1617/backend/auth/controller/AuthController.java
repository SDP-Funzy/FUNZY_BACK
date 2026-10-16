package com.sdp1617.backend.auth.controller;

import com.sdp1617.backend.global.error.ErrorCode;
import com.sdp1617.backend.global.error.ApiErrorCodes;
import com.sdp1617.backend.auth.dto.AccountUnlockConfirmRequest;
import com.sdp1617.backend.auth.dto.AccountUnlockRequest;
import com.sdp1617.backend.auth.dto.EmailCodeSendRequest;
import com.sdp1617.backend.auth.dto.EmailCodeVerifyRequest;
import com.sdp1617.backend.auth.dto.EmailCodeVerifyResponse;
import com.sdp1617.backend.auth.dto.LoginIdFindRequest;
import com.sdp1617.backend.auth.dto.LoginRequest;
import com.sdp1617.backend.auth.dto.NicknameCheckResponse;
import com.sdp1617.backend.auth.dto.NicknamePolicy;
import com.sdp1617.backend.auth.dto.PasswordResetConfirmRequest;
import com.sdp1617.backend.auth.dto.PasswordResetRequest;
import com.sdp1617.backend.auth.dto.SignUpRequest;
import com.sdp1617.backend.auth.dto.TokenResponse;
import com.sdp1617.backend.auth.service.AuthService;
import com.sdp1617.backend.auth.service.EmailCodeService;
import com.sdp1617.backend.global.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import com.sdp1617.backend.global.config.ApiTags;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
@Validated
@Tag(name = ApiTags.AUTH)
public class AuthController {

    private final AuthService authService;
    private final EmailCodeService emailCodeService;

    @Operation(summary = "이메일 인증번호 발송", description = """
            회원가입 첫 단계로, 입력한 이메일로 6자리 인증번호를 발송합니다.
            - 이미 가입된 이메일(소셜 가입 포함)이면 AUTH_006으로 거부합니다. 가입 여부 조회 남용을 막기 위해 아래 쿨다운/요청 제한을 먼저 적용합니다.
            - 인증번호는 5분간 유효하며, 새로 발송하면 이전 인증번호는 무효가 됩니다.
            - 재발송은 1분에 한 번만 가능합니다(AUTH_024).
            - 남용 방지를 위해 10분 동안 이메일당 5회·IP당 20회, 하루 동안 이메일당 20회까지만 요청할 수 있습니다(AUTH_025).
            """)
    @PostMapping("/email/code")
    @ApiErrorCodes({ErrorCode.COMMON_002, ErrorCode.AUTH_006, ErrorCode.AUTH_024, ErrorCode.AUTH_025})
    public ApiResponse<Void> sendEmailCode(
            @Valid @RequestBody EmailCodeSendRequest request, HttpServletRequest httpRequest
    ) {
        emailCodeService.sendCode(resolveClientIp(httpRequest), request.email());
        return ApiResponse.ok("인증번호를 발송했습니다.", null);
    }

    @Operation(summary = "이메일 인증번호 확인", description = """
            메일로 받은 인증번호를 확인하고, 회원가입에 사용할 인증 완료 토큰을 발급합니다.
            - 발급된 verificationToken을 회원가입 요청에 그대로 담아 보내면 됩니다(30분 유효).
            - 인증번호를 5회 틀리면 해당 인증번호는 폐기되며, 인증번호를 다시 받아야 합니다(AUTH_023).
            - 남용 방지를 위해 10분 동안 IP당 틀린 요청(불일치·횟수 초과)이 20회를 넘으면 차단됩니다(AUTH_025). 성공·만료 응답은 세지 않습니다.
            """)
    @PostMapping("/email/code/verify")
    @ApiErrorCodes({ErrorCode.COMMON_002, ErrorCode.AUTH_021, ErrorCode.AUTH_022, ErrorCode.AUTH_023, ErrorCode.AUTH_025})
    public ApiResponse<EmailCodeVerifyResponse> verifyEmailCode(
            @Valid @RequestBody EmailCodeVerifyRequest request, HttpServletRequest httpRequest
    ) {
        String token = emailCodeService.verifyCode(resolveClientIp(httpRequest), request.email(), request.code());
        return ApiResponse.ok("이메일 인증이 완료되었습니다.", new EmailCodeVerifyResponse(token));
    }

    @Operation(summary = "회원가입", description = """
            이메일 인증을 마친 뒤 아이디/비밀번호로 신규 가입합니다.
            - 이메일은 인증번호 확인 API에서 받은 verificationToken으로 결정되며, 가입 즉시 인증된 계정이 됩니다.
            - verificationToken이 만료(30분)되었거나 유효하지 않으면 AUTH_026으로 거부하며, 이메일 인증부터 다시 진행해야 합니다.
            - verificationToken 필드 자체를 보내지 않으면 입력값 검증 오류(COMMON_002, "이메일 인증이 필요합니다.")입니다.
            - 비밀번호는 8자 이상, 영문+숫자+특수문자 조합이어야 하고 72바이트를 넘을 수 없습니다(COMMON_002).
            - termsAgreed(이용약관·개인정보 수집 동의)와 age14Confirmed(만 14세 이상)는 true여야 합니다(COMMON_002).
            - 아이디(필드 이름은 `nickname`)는 다른 회원에게 보이는 공개 아이디이자 로그인 아이디이며, 중복될 수 없습니다. 2~20자, 영문 소문자·숫자·마침표(.)·밑줄(_)만 쓸 수 있고
              마침표로 시작·끝나거나 연달아 쓸 수 없습니다(어기면 COMMON_002). 대문자는 앱에서 소문자로 바꿔 보내 주세요.
            - 가입 완료 후 자동 로그인되지 않으며, 로그인 화면으로 이동해 별도로 로그인해야 합니다.
            """)
    @PostMapping("/signup")
    @ApiErrorCodes({ErrorCode.COMMON_002, ErrorCode.AUTH_006, ErrorCode.AUTH_007, ErrorCode.AUTH_008, ErrorCode.AUTH_026})
    public ApiResponse<Void> signUp(@Valid @RequestBody SignUpRequest request) {
        authService.signUp(request);
        return ApiResponse.ok("회원가입이 완료되었습니다.", null);
    }

    @Operation(summary = "아이디 중복 확인", description = """
            아이디 사용 가능 여부를 실시간으로 확인합니다. 규칙에 어긋나면 COMMON_002로 규칙을 안내합니다.
            - 회원가입, 소셜 회원가입, 아이디 변경 화면에서 공통으로 사용합니다.
            - 아이디 변경 화면에서는 access token을 함께 보내 주세요. 로그인한 회원은 본인을 빼고 확인합니다
              (예: 규칙 전 대문자 아이디 "Tiki"를 "tiki"로 바꾸는 경우 사용 가능). 대소문자만 다른 아이디는 같은 아이디로 봅니다.
            """)
    @GetMapping("/nickname/check")
    @ApiErrorCodes({ErrorCode.COMMON_002})
    public ApiResponse<NicknameCheckResponse> checkNickname(
            @Parameter(description = "확인할 아이디", example = "tiki_kim") @RequestParam String nickname,
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId
    ) {
        // 가입·변경 요청처럼 앞뒤 공백을 자른 값으로 같은 규칙을 검사한다
        String checked = NicknamePolicy.requireValid(nickname);
        NicknameCheckResponse response = new NicknameCheckResponse(authService.isNicknameAvailable(checked, memberId));
        return ApiResponse.ok("아이디 사용 가능 여부를 조회했습니다.", response);
    }

    @Operation(summary = "아이디 로그인", description = """
            아이디/비밀번호로 로그인합니다.
            - 존재하지 않는 아이디, 비밀번호 불일치, 소셜 전용 계정으로 로그인 시도한 경우 모두 동일한 오류(AUTH_001)로 응답합니다. 계정 존재 여부가 외부에 드러나지 않도록 하기 위한 의도된 동작입니다.
            - 같은 IP에서 5회 로그인에 실패하면 그 IP에서의 로그인이 15분간 잠깁니다(실패 횟수는 첫 실패부터 15분간 누적, 로그인 성공 시 그 IP 기록 초기화). 다른 네트워크에서는 그대로 로그인할 수 있습니다.
            - 모든 IP를 합쳐 30회 실패하면 계정 전체가 15분간 잠깁니다(여러 IP로 나눠 비밀번호를 대입하는 것 방지).
            - 두 경우 모두 AUTH_010이며, 계정 잠금 해제 메일이나 비밀번호 재설정으로 바로 풀 수 있습니다.
            """)
    @PostMapping("/login")
    @ApiErrorCodes({ErrorCode.COMMON_002, ErrorCode.AUTH_001, ErrorCode.AUTH_010})
    public ApiResponse<TokenResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        TokenResponse response = authService.login(resolveClientIp(httpRequest), request);
        return ApiResponse.ok("로그인에 성공하였습니다.", response);
    }

    @Operation(summary = "아이디 찾기", description = """
            가입할 때 인증한 이메일로 아이디를 메일로 보내줍니다.
            - 가입되지 않은 이메일이어도 항상 동일하게 200을 반환합니다(계정 존재 여부 비노출). 실제 메일은 가입된 이메일에만 발송됩니다.
            - 소셜 전용 계정은 아이디로 로그인할 수 없으므로, 아이디 대신 소셜 계정으로 로그인하라는 안내 메일이 발송됩니다.
            - 남용 방지를 위해 IP/이메일 기준으로 rate limit이 적용됩니다. 제한을 초과해도 존재 여부가 드러나지 않도록 동일하게 200을 반환하고 메일만 조용히 보내지 않습니다.
            """)
    @PostMapping("/login-id/find")
    @ApiErrorCodes({ErrorCode.COMMON_002})
    public ApiResponse<Void> findLoginId(
            @Valid @RequestBody LoginIdFindRequest request, HttpServletRequest httpRequest
    ) {
        authService.requestLoginIdReminder(resolveClientIp(httpRequest), request.email());
        return ApiResponse.ok("가입된 이메일이라면 아이디 안내 메일을 발송했습니다.", null);
    }

    @Operation(summary = "비밀번호 재설정 링크 발송", description = """
            입력한 이메일로 비밀번호 재설정 링크를 발송합니다.
            - 가입되지 않은 이메일이거나 소셜 전용 계정이어도 항상 동일하게 200을 반환합니다(계정 존재 여부 비노출). 실제 메일은 가입된 이메일 계정에만 발송됩니다.
            - 발송된 링크는 15분간 유효합니다.
            - 남용 방지를 위해 IP/이메일 기준으로 rate limit이 적용됩니다. 제한을 초과해도 존재 여부가 드러나지 않도록 동일하게 200을 반환하고 메일만 조용히 보내지 않습니다.
            """)
    @PostMapping("/password/reset-request")
    @ApiErrorCodes({ErrorCode.COMMON_002})
    public ApiResponse<Void> requestPasswordReset(
            @Valid @RequestBody PasswordResetRequest request, HttpServletRequest httpRequest
    ) {
        authService.requestPasswordReset(resolveClientIp(httpRequest), request.email());
        return ApiResponse.ok("비밀번호 재설정 링크를 발송했습니다.", null);
    }

    @Operation(summary = "비밀번호 재설정", description = """
            이메일로 받은 토큰으로 비밀번호를 재설정합니다.
            - 새 비밀번호가 현재 비밀번호와 같으면 재설정할 수 없습니다(AUTH_028).
            - 링크는 한 번만 쓸 수 있습니다. 링크를 확인한 뒤 실패하면(AUTH_028 포함) 링크는 사용된 것으로 처리되어, 다시 시도하려면 재설정 메일을 다시 요청해야 합니다(AUTH_011).
              새 비밀번호 확인 불일치(AUTH_008)와 입력값 오류(COMMON_002)는 링크를 확인하기 전에 걸러지므로 같은 링크로 다시 시도할 수 있습니다.
            - 재설정 성공 시 계정 잠금이 함께 해제되고, 로그인되어 있던 기기의 세션도 모두 종료됩니다.
              이미 발급된 access token도 바로 쓸 수 없게 됩니다(AUTH_027) — 계정 탈취가 의심될 때 기존 로그인을 즉시 끊기 위함.
            - 링크를 받은 뒤 탈퇴한 계정이면 AUTH_002입니다.
            """)
    @PostMapping("/password/reset")
    @ApiErrorCodes({ErrorCode.COMMON_002, ErrorCode.AUTH_002, ErrorCode.AUTH_008, ErrorCode.AUTH_011, ErrorCode.AUTH_028})
    public ApiResponse<Void> resetPassword(@Valid @RequestBody PasswordResetConfirmRequest request) {
        authService.resetPassword(request.token(), request.newPassword(), request.newPasswordConfirm());
        return ApiResponse.ok("비밀번호가 재설정되었습니다.", null);
    }

    @Operation(summary = "계정 잠금 해제 링크 발송", description = """
            로그인 실패로 잠긴 계정의 잠금 해제 링크를 이메일로 발송합니다(모든 IP의 잠금이 해제됨).
            - 가입되지 않은 이메일이어도 항상 동일하게 200을 반환합니다(계정 존재 여부 비노출).
            - 발송된 링크는 15분간 유효합니다.
            - 남용 방지를 위해 IP/이메일 기준으로 rate limit이 적용됩니다. 제한을 초과해도 존재 여부가 드러나지 않도록 동일하게 200을 반환하고 메일만 조용히 보내지 않습니다.
            """)
    @PostMapping("/account/unlock-request")
    @ApiErrorCodes({ErrorCode.COMMON_002})
    public ApiResponse<Void> requestAccountUnlock(
            @Valid @RequestBody AccountUnlockRequest request, HttpServletRequest httpRequest
    ) {
        authService.requestAccountUnlock(resolveClientIp(httpRequest), request.email());
        return ApiResponse.ok("계정 잠금 해제 링크를 발송했습니다.", null);
    }

    /**
     * 운영에서는 caddy(리버스 프록시)를 거쳐 들어오므로, server.forward-headers-strategy=native 설정에 따라
     * Tomcat이 X-Forwarded-For의 실제 사용자 IP로 remoteAddr를 바꿔준다. 이 값은 신뢰할 수 있다:
     * - Tomcat은 사설 대역(docker 네트워크의 caddy 등)에서 온 요청의 X-Forwarded-For만 믿는다
     * - caddy는 클라이언트가 보낸 X-Forwarded-For를 그대로 넘기지 않고 실제 접속 IP로 채운다
     * - app 포트(8080)는 서버 내부(127.0.0.1)에만 열려 있어 외부에서 caddy를 우회할 수 없다
     */
    private String resolveClientIp(HttpServletRequest request) {
        return request.getRemoteAddr();
    }

    @Operation(summary = "계정 잠금 해제", description = """
            이메일로 받은 토큰으로 계정 잠금을 해제합니다.
            - 해제 성공 시 모든 IP의 실패 횟수가 초기화되고, 로그인되어 있던 기기의 세션도 모두 종료됩니다.
              이미 발급된 access token도 바로 쓸 수 없게 됩니다(AUTH_027).
            - 링크를 받은 뒤 탈퇴한 계정이면 AUTH_002입니다.
            """)
    @PostMapping("/account/unlock")
    @ApiErrorCodes({ErrorCode.COMMON_002, ErrorCode.AUTH_002, ErrorCode.AUTH_011})
    public ApiResponse<Void> unlockAccount(@Valid @RequestBody AccountUnlockConfirmRequest request) {
        authService.unlockAccount(request.token());
        return ApiResponse.ok("계정 잠금이 해제되었습니다.", null);
    }
}
