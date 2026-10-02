package com.sdp1617.backend.auth.service;

import com.sdp1617.backend.auth.email.EmailSender;
import com.sdp1617.backend.auth.email.EmailTemplateRenderer;
import com.sdp1617.backend.auth.repository.EmailCodeRepository;
import com.sdp1617.backend.auth.repository.MemberRepository;
import com.sdp1617.backend.auth.repository.VerificationTokenRepository;
import com.sdp1617.backend.global.error.CustomException;
import com.sdp1617.backend.global.error.ErrorCode;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 회원가입 전 이메일 본인 확인: 인증번호 발송 → 인증번호 확인 → 인증 완료 토큰 발급.
 * 발급된 토큰은 회원가입 요청에 실려 와서 {@link AuthService#signUp}에서 이메일로 교환된다.
 *
 * 가입 전 단계라 DB에 쓰는 것이 없어 트랜잭션 없이 동작하고, 메일도 이벤트 대신 바로 발송한다
 * (트랜잭션이 없으면 AFTER_COMMIT 리스너가 호출되지 않는다).
 */
@Service
@RequiredArgsConstructor
public class EmailCodeService {

    /** 인증 완료 토큰의 {@link VerificationTokenRepository} purpose. 값은 인증된 이메일. */
    static final String VERIFIED_EMAIL_PURPOSE = "signup-email-verified";
    static final String SEND_RATE_LIMIT_PURPOSE = "email-code";
    static final String VERIFY_RATE_LIMIT_PURPOSE = "email-code-verify";
    static final Duration CODE_TTL = Duration.ofMinutes(5);
    static final Duration RESEND_COOLDOWN = Duration.ofMinutes(1);
    static final Duration VERIFIED_TOKEN_TTL = Duration.ofMinutes(30);
    static final int MAX_VERIFY_ATTEMPTS = 5;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final MemberRepository memberRepository;
    private final EmailCodeRepository emailCodeRepository;
    private final VerificationTokenRepository verificationTokenRepository;
    private final VerificationRequestRateLimiter rateLimiter;
    private final EmailSender emailSender;
    private final EmailTemplateRenderer emailTemplateRenderer;

    @Value("${app.mail.logo-url}")
    private String logoUrl;

    /**
     * 쿨다운/rate limit을 이메일 중복 확인보다 먼저 거친다. 중복이면 AUTH_006을 돌려주므로(프론트 요구사항)
     * 이 API로 가입 여부를 조회할 수 있는데, 제한보다 중복 확인이 앞서면 그 조회가 무제한·DB 비용으로 가능해진다.
     */
    public void sendCode(String clientIp, String email) {
        if (!emailCodeRepository.tryStartCooldown(email, RESEND_COOLDOWN)) {
            throw new CustomException(ErrorCode.AUTH_024);
        }
        if (!rateLimiter.isAllowed(SEND_RATE_LIMIT_PURPOSE, clientIp, email)
                || !rateLimiter.isAllowedDaily(SEND_RATE_LIMIT_PURPOSE, email)) {
            // 한도에 걸린 요청이 쿨다운까지 잡아두면 다음 요청이 엉뚱하게 AUTH_024를 받으므로 풀어준다.
            emailCodeRepository.clearCooldown(email);
            throw new CustomException(ErrorCode.AUTH_025);
        }
        if (memberRepository.existsByEmail(email)) {
            throw new CustomException(ErrorCode.AUTH_006);
        }

        String code = generateCode();
        try {
            // 저장을 발송 성공 뒤로 미뤄서, 렌더링이나 발송이 실패해도 사용자가 직전에 받은 인증번호는 그대로 쓸 수 있게 한다.
            // (발송~저장 사이 수 ms 동안 사용자가 새 번호를 입력할 수는 없으므로 순서를 바꿔도 문제없다)
            String html = emailTemplateRenderer.render("verification-code", Map.of(
                    "code", code,
                    "ttlMinutes", CODE_TTL.toMinutes(),
                    "logoUrl", logoUrl
            ));
            String plainText = "이메일 인증번호\n\n인증번호: " + code
                    + "\n\n(" + CODE_TTL.toMinutes() + "분간 유효합니다)";
            emailSender.send(email, "[Funzy] 이메일 인증번호 안내", plainText, html);
            emailCodeRepository.saveCode(email, code, CODE_TTL);
        } catch (RuntimeException exception) {
            // 메일이 안 나갔는데 쿨다운만 남으면 사용자가 1분간 재시도조차 못 하므로 풀어준다.
            emailCodeRepository.clearCooldown(email);
            throw exception;
        }
    }

    /**
     * 인증번호가 맞으면 회원가입에 사용할 인증 완료 토큰을 반환한다.
     * 틀린 횟수는 이메일 단위로 세므로(인증번호 추측 방지), 제3자가 남의 이메일로 일부러 틀려 인증번호를
     * 폐기시키는 것까지는 막지 못한다. 대량으로 하지 못하도록 IP 단위로 실패 횟수를 제한한다 — 이메일 단위로
     * 제한하면 오히려 피해자 본인의 재시도까지 막히게 된다.
     */
    public String verifyCode(String clientIp, String email, String code) {
        if (!rateLimiter.tryReserveForIp(VERIFY_RATE_LIMIT_PURPOSE, clientIp)) {
            throw new CustomException(ErrorCode.AUTH_025);
        }
        EmailCodeRepository.VerifyResult result;
        try {
            result = emailCodeRepository.verify(email, code, MAX_VERIFY_ATTEMPTS);
        } catch (RuntimeException exception) {
            // 확인 자체가 실패(Redis 오류 등)했으면 추측이 일어나지 않았으므로 예약을 돌려준다.
            rateLimiter.releaseForIp(VERIFY_RATE_LIMIT_PURPOSE, clientIp);
            throw exception;
        }
        // 추측 시도에 해당하는 결과(틀림·횟수 초과)만 예약을 남긴다. EXPIRED는 이미 성공한 확인을 재시도한 경우
        // (응답 유실·중복 클릭)에도 나오는데, 그걸 세면 IP를 공유하는 정상 사용자들이 한도에 몰린다.
        if (result == EmailCodeRepository.VerifyResult.MATCHED || result == EmailCodeRepository.VerifyResult.EXPIRED) {
            rateLimiter.releaseForIp(VERIFY_RATE_LIMIT_PURPOSE, clientIp);
        }
        return switch (result) {
            case MATCHED -> verificationTokenRepository.issueValue(VERIFIED_EMAIL_PURPOSE, email, VERIFIED_TOKEN_TTL);
            case MISMATCHED -> throw new CustomException(ErrorCode.AUTH_021);
            case ATTEMPTS_EXCEEDED -> throw new CustomException(ErrorCode.AUTH_023);
            case EXPIRED -> throw new CustomException(ErrorCode.AUTH_022);
        };
    }

    private String generateCode() {
        return String.format("%06d", RANDOM.nextInt(1_000_000));
    }
}
