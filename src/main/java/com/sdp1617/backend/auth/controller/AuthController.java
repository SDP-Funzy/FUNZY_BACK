package com.sdp1617.backend.auth.controller;

import com.sdp1617.backend.auth.dto.AccountUnlockConfirmRequest;
import com.sdp1617.backend.auth.dto.AccountUnlockRequest;
import com.sdp1617.backend.auth.dto.EmailCodeSendRequest;
import com.sdp1617.backend.auth.dto.EmailCodeVerifyRequest;
import com.sdp1617.backend.auth.dto.EmailCodeVerifyResponse;
import com.sdp1617.backend.auth.dto.LoginIdFindRequest;
import com.sdp1617.backend.auth.dto.LoginRequest;
import com.sdp1617.backend.auth.dto.NicknameCheckResponse;
import com.sdp1617.backend.auth.dto.PasswordResetConfirmRequest;
import com.sdp1617.backend.auth.dto.PasswordResetRequest;
import com.sdp1617.backend.auth.dto.SignUpRequest;
import com.sdp1617.backend.auth.dto.TokenResponse;
import com.sdp1617.backend.auth.service.AuthService;
import com.sdp1617.backend.auth.service.EmailCodeService;
import com.sdp1617.backend.global.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
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
@Tag(name = "회원 인증", description = "회원가입(이메일 인증번호), 아이디 로그인, 아이디·비밀번호 찾기, 계정 잠금 해제 API")
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
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "발송 성공",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {
                              "success": true,
                              "code": "200",
                              "message": "인증번호를 발송했습니다.",
                              "data": null
                            }
                            """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "이미 가입된 이메일",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {
                              "success": false,
                              "code": "AUTH_006",
                              "message": "이미 가입된 이메일입니다.",
                              "data": null
                            }
                            """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "재발송 쿨다운 / 요청 횟수 초과",
                    content = @Content(mediaType = "application/json", examples = {
                            @ExampleObject(name = "재발송 쿨다운", value = """
                                    {
                                      "success": false,
                                      "code": "AUTH_024",
                                      "message": "인증번호는 1분 후에 다시 요청할 수 있습니다.",
                                      "data": null
                                    }
                                    """),
                            @ExampleObject(name = "요청 횟수 초과", value = """
                                    {
                                      "success": false,
                                      "code": "AUTH_025",
                                      "message": "인증번호 요청 횟수를 초과했습니다. 잠시 후 다시 시도해주세요.",
                                      "data": null
                                    }
                                    """)
                    }))
    })
    @PostMapping("/email/code")
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
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "인증 성공",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = EmailCodeVerifyResponse.class),
                            examples = @ExampleObject(value = """
                            {
                              "success": true,
                              "code": "200",
                              "message": "이메일 인증이 완료되었습니다.",
                              "data": { "verificationToken": "a1b2c3d4-e5f6-7890-abcd-ef1234567890" }
                            }
                            """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "인증번호 불일치 / 만료 / 시도 횟수 초과",
                    content = @Content(mediaType = "application/json", examples = {
                            @ExampleObject(name = "인증번호 불일치", value = """
                                    {
                                      "success": false,
                                      "code": "AUTH_021",
                                      "message": "인증번호가 일치하지 않습니다.",
                                      "data": null
                                    }
                                    """),
                            @ExampleObject(name = "인증번호 만료", value = """
                                    {
                                      "success": false,
                                      "code": "AUTH_022",
                                      "message": "인증번호가 만료되었습니다. 인증번호를 다시 받아주세요.",
                                      "data": null
                                    }
                                    """),
                            @ExampleObject(name = "시도 횟수 초과", value = """
                                    {
                                      "success": false,
                                      "code": "AUTH_023",
                                      "message": "인증번호 입력 횟수를 초과했습니다. 인증번호를 다시 받아주세요.",
                                      "data": null
                                    }
                                    """)
                    })),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "요청 횟수 초과",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {
                              "success": false,
                              "code": "AUTH_025",
                              "message": "인증번호 요청 횟수를 초과했습니다. 잠시 후 다시 시도해주세요.",
                              "data": null
                            }
                            """)))
    })
    @PostMapping("/email/code/verify")
    public ApiResponse<EmailCodeVerifyResponse> verifyEmailCode(
            @Valid @RequestBody EmailCodeVerifyRequest request, HttpServletRequest httpRequest
    ) {
        String token = emailCodeService.verifyCode(resolveClientIp(httpRequest), request.email(), request.code());
        return ApiResponse.ok("이메일 인증이 완료되었습니다.", new EmailCodeVerifyResponse(token));
    }

    @Operation(summary = "회원가입", description = """
            이메일 인증을 마친 뒤 아이디(닉네임)/비밀번호로 신규 가입합니다.
            - 이메일은 인증번호 확인 API에서 받은 verificationToken으로 결정되며, 가입 즉시 인증된 계정이 됩니다.
            - verificationToken이 만료(30분)되었거나 유효하지 않으면 AUTH_026으로 거부하며, 이메일 인증부터 다시 진행해야 합니다.
            - verificationToken 필드 자체를 보내지 않으면 입력값 검증 오류(COMMON_002, "이메일 인증이 필요합니다.")입니다.
            - 비밀번호는 8자 이상, 영문+숫자+특수문자 조합이어야 합니다.
            - 닉네임은 로그인 아이디로 사용되며, 2~20자 이내여야 하고 중복될 수 없습니다.
            - 가입 완료 후 자동 로그인되지 않으며, 로그인 화면으로 이동해 별도로 로그인해야 합니다.
            """)
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "가입 성공",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {
                              "success": true,
                              "code": "201",
                              "message": "회원가입이 완료되었습니다.",
                              "data": null
                            }
                            """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "이메일/닉네임 중복",
                    content = @Content(mediaType = "application/json", examples = {
                            @ExampleObject(name = "이메일 중복", value = """
                                    {
                                      "success": false,
                                      "code": "AUTH_006",
                                      "message": "이미 가입된 이메일입니다.",
                                      "data": null
                                    }
                                    """),
                            @ExampleObject(name = "닉네임 중복", value = """
                                    {
                                      "success": false,
                                      "code": "AUTH_007",
                                      "message": "이미 사용 중인 닉네임입니다.",
                                      "data": null
                                    }
                                    """)
                    })),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "비밀번호 확인 불일치 / 이메일 인증 만료",
                    content = @Content(mediaType = "application/json", examples = {
                            @ExampleObject(name = "비밀번호 확인 불일치", value = """
                                    {
                                      "success": false,
                                      "code": "AUTH_008",
                                      "message": "비밀번호가 일치하지 않습니다.",
                                      "data": null
                                    }
                                    """),
                            @ExampleObject(name = "이메일 인증 만료", value = """
                                    {
                                      "success": false,
                                      "code": "AUTH_026",
                                      "message": "이메일 인증이 만료되었거나 유효하지 않습니다. 이메일 인증을 다시 진행해주세요.",
                                      "data": null
                                    }
                                    """)
                    }))
    })
    @PostMapping("/signup")
    public ApiResponse<Void> signUp(@Valid @RequestBody SignUpRequest request) {
        authService.signUp(request);
        return ApiResponse.created("회원가입이 완료되었습니다.", null);
    }

    @Operation(summary = "아이디(닉네임) 중복 확인", description = """
            닉네임 사용 가능 여부를 실시간으로 확인합니다.
            - 회원가입, 소셜 회원가입, 닉네임 변경 화면에서 공통으로 사용합니다.
            """)
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = NicknameCheckResponse.class),
                            examples = {
                            @ExampleObject(name = "사용 가능", value = """
                                    {
                                      "success": true,
                                      "code": "200",
                                      "message": "닉네임 사용 가능 여부를 조회했습니다.",
                                      "data": { "available": true }
                                    }
                                    """),
                            @ExampleObject(name = "이미 사용 중", value = """
                                    {
                                      "success": true,
                                      "code": "200",
                                      "message": "닉네임 사용 가능 여부를 조회했습니다.",
                                      "data": { "available": false }
                                    }
                                    """)
                    }))
    })
    @GetMapping("/nickname/check")
    public ApiResponse<NicknameCheckResponse> checkNickname(
            @RequestParam @NotBlank(message = "닉네임을 입력해주세요.") String nickname
    ) {
        NicknameCheckResponse response = new NicknameCheckResponse(authService.isNicknameAvailable(nickname));
        return ApiResponse.ok("닉네임 사용 가능 여부를 조회했습니다.", response);
    }

    @Operation(summary = "아이디 로그인", description = """
            아이디(닉네임)/비밀번호로 로그인합니다.
            - 존재하지 않는 아이디, 비밀번호 불일치, 소셜 전용 계정으로 로그인 시도한 경우 모두 동일한 오류(AUTH_001)로 응답합니다. 계정 존재 여부가 외부에 드러나지 않도록 하기 위한 의도된 동작입니다.
            - 같은 IP에서 5회 로그인에 실패하면 그 IP에서의 로그인이 15분간 잠깁니다(실패 횟수는 첫 실패부터 15분간 누적, 로그인 성공 시 그 IP 기록 초기화). 다른 네트워크에서는 그대로 로그인할 수 있습니다.
            - 모든 IP를 합쳐 30회 실패하면 계정 전체가 15분간 잠깁니다(여러 IP로 나눠 비밀번호를 대입하는 것 방지).
            - 두 경우 모두 AUTH_010이며, 계정 잠금 해제 메일이나 비밀번호 재설정으로 바로 풀 수 있습니다.
            """)
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "로그인 성공",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = TokenResponse.class),
                            examples = @ExampleObject(value = """
                            {
                              "success": true,
                              "code": "200",
                              "message": "로그인에 성공하였습니다.",
                              "data": {
                                "accessToken": "eyJhbGciOiJIUzM4NCJ9...",
                                "refreshToken": "eyJhbGciOiJIUzM4NCJ9..."
                              }
                            }
                            """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패 (아이디 없음/비밀번호 불일치/소셜전용 계정 공통)",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {
                              "success": false,
                              "code": "AUTH_001",
                              "message": "아이디 또는 비밀번호가 일치하지 않습니다.",
                              "data": null
                            }
                            """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "423", description = "로그인 시도 초과로 잠김 (IP 5회 / 계정 전체 30회)",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {
                              "success": false,
                              "code": "AUTH_010",
                              "message": "로그인 시도가 많아 일시적으로 잠겼습니다. 15분 후 다시 시도하거나 이메일 인증으로 잠금을 해제해주세요.",
                              "data": null
                            }
                            """)))
    })
    @PostMapping("/login")
    public ApiResponse<TokenResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        TokenResponse response = authService.login(resolveClientIp(httpRequest), request);
        return ApiResponse.ok("로그인에 성공하였습니다.", response);
    }

    @Operation(summary = "아이디 찾기", description = """
            가입할 때 인증한 이메일로 아이디(닉네임)를 메일로 보내줍니다.
            - 가입되지 않은 이메일이어도 항상 동일하게 200을 반환합니다(계정 존재 여부 비노출). 실제 메일은 가입된 이메일에만 발송됩니다.
            - 소셜 전용 계정은 아이디로 로그인할 수 없으므로, 아이디 대신 소셜 계정으로 로그인하라는 안내 메일이 발송됩니다.
            - 남용 방지를 위해 IP/이메일 기준으로 rate limit이 적용됩니다. 제한을 초과해도 존재 여부가 드러나지 않도록 동일하게 200을 반환하고 메일만 조용히 보내지 않습니다.
            """)
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "요청 접수 (실제 존재 여부와 무관하게 항상 200)",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {
                              "success": true,
                              "code": "200",
                              "message": "가입된 이메일이라면 아이디 안내 메일을 발송했습니다.",
                              "data": null
                            }
                            """)))
    })
    @PostMapping("/login-id/find")
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
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "요청 접수 (실제 존재 여부와 무관하게 항상 200)",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {
                              "success": true,
                              "code": "200",
                              "message": "비밀번호 재설정 링크를 발송했습니다.",
                              "data": null
                            }
                            """)))
    })
    @PostMapping("/password/reset-request")
    public ApiResponse<Void> requestPasswordReset(
            @Valid @RequestBody PasswordResetRequest request, HttpServletRequest httpRequest
    ) {
        authService.requestPasswordReset(resolveClientIp(httpRequest), request.email());
        return ApiResponse.ok("비밀번호 재설정 링크를 발송했습니다.", null);
    }

    @Operation(summary = "비밀번호 재설정", description = """
            이메일로 받은 토큰으로 비밀번호를 재설정합니다.
            - 재설정 성공 시 계정 잠금이 함께 해제되고, 로그인되어 있던 다른 기기의 세션도 모두 종료됩니다.
            """)
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "재설정 성공",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {
                              "success": true,
                              "code": "200",
                              "message": "비밀번호가 재설정되었습니다.",
                              "data": null
                            }
                            """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "새 비밀번호 확인 불일치 / 토큰 만료 또는 유효하지 않음",
                    content = @Content(mediaType = "application/json", examples = {
                            @ExampleObject(name = "새 비밀번호 확인 불일치", value = """
                                    {
                                      "success": false,
                                      "code": "AUTH_008",
                                      "message": "비밀번호가 일치하지 않습니다.",
                                      "data": null
                                    }
                                    """),
                            @ExampleObject(name = "토큰 만료 또는 유효하지 않음", value = """
                                    {
                                      "success": false,
                                      "code": "AUTH_011",
                                      "message": "유효하지 않거나 만료된 링크입니다.",
                                      "data": null
                                    }
                                    """)
                    }))
    })
    @PostMapping("/password/reset")
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
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "요청 접수 (실제 존재 여부와 무관하게 항상 200)",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {
                              "success": true,
                              "code": "200",
                              "message": "계정 잠금 해제 링크를 발송했습니다.",
                              "data": null
                            }
                            """)))
    })
    @PostMapping("/account/unlock-request")
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
            - 해제 성공 시 모든 IP의 실패 횟수가 초기화되고, 로그인되어 있던 다른 기기의 세션도 모두 종료됩니다.
            """)
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "잠금 해제 성공",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {
                              "success": true,
                              "code": "200",
                              "message": "계정 잠금이 해제되었습니다.",
                              "data": null
                            }
                            """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "토큰 만료 또는 유효하지 않음",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {
                              "success": false,
                              "code": "AUTH_011",
                              "message": "유효하지 않거나 만료된 링크입니다.",
                              "data": null
                            }
                            """)))
    })
    @PostMapping("/account/unlock")
    public ApiResponse<Void> unlockAccount(@Valid @RequestBody AccountUnlockConfirmRequest request) {
        authService.unlockAccount(request.token());
        return ApiResponse.ok("계정 잠금이 해제되었습니다.", null);
    }
}
