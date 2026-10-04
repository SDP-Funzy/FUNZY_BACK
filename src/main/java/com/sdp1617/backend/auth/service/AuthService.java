package com.sdp1617.backend.auth.service;

import com.sdp1617.backend.auth.dto.LoginRequest;
import com.sdp1617.backend.auth.dto.SignUpRequest;
import com.sdp1617.backend.auth.email.EmailTemplateRenderer;
import com.sdp1617.backend.auth.email.VerificationLinkIssuedEvent;
import com.sdp1617.backend.auth.entity.AuthProvider;
import com.sdp1617.backend.auth.entity.Member;
import com.sdp1617.backend.auth.entity.SocialConnection;
import com.sdp1617.backend.auth.dto.TokenResponse;
import com.sdp1617.backend.auth.repository.MemberRepository;
import com.sdp1617.backend.auth.repository.SocialConnectionRepository;
import com.sdp1617.backend.auth.repository.VerificationTokenRepository;
import com.sdp1617.backend.auth.util.Emails;
import com.sdp1617.backend.global.common.AfterCommit;
import com.sdp1617.backend.global.error.ConstraintViolations;
import com.sdp1617.backend.global.error.CustomException;
import com.sdp1617.backend.global.error.ErrorCode;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private static final String PASSWORD_RESET_PURPOSE = "password-reset";
    private static final String ACCOUNT_UNLOCK_PURPOSE = "account-unlock";
    private static final String LOGIN_ID_FIND_PURPOSE = "login-id-find";
    private static final Duration VERIFICATION_TOKEN_TTL = Duration.ofMinutes(15);

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final LoginAttemptRecorder loginAttemptRecorder;
    private final VerificationTokenRepository verificationTokenRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final EmailTemplateRenderer emailTemplateRenderer;
    private final VerificationRequestRateLimiter rateLimiter;
    private final TransactionTemplate transactionTemplate;
    private final SocialConnectionRepository socialConnectionRepository;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Value("${app.mail.logo-url}")
    private String logoUrl;

    /**
     * 인증 완료 토큰을 이메일로 교환해 이미 인증된 계정으로 가입시킨다({@link EmailCodeService} 참고).
     * 토큰은 가입이 성공한 뒤에만 폐기한다 — 먼저 폐기하면 닉네임 중복처럼 사용자가 고쳐서 다시 시도할 수 있는
     * 실패에서도 이메일 인증을 처음부터 다시 해야 한다. 같은 토큰으로 동시에 가입을 시도하면(중복 제출 등)
     * 둘 다 existsByEmail을 통과할 수 있는데, 이때는 이메일 유니크 제약이 막고 AUTH_006으로 응답한다.
     * 폐기는 커밋 이후에 한다 — 커밋 전에 지웠다가 커밋이 실패하면 회원은 안 만들어졌는데 토큰만 사라진다.
     */
    @Transactional
    public void signUp(SignUpRequest request) {
        // 중복 확인과 저장(Member 생성자)이 같은 값을 쓰도록 여기서 정규화한다. 비어 있으면 유효하지 않은 토큰으로 본다.
        String email = verificationTokenRepository
                .findValue(EmailCodeService.VERIFIED_EMAIL_PURPOSE, request.verificationToken())
                .map(Emails::normalize)
                .orElseThrow(() -> new CustomException(ErrorCode.AUTH_026));

        if (memberRepository.existsByEmail(email)) {
            throw new CustomException(ErrorCode.AUTH_006);
        }
        if (memberRepository.isNicknameTaken(request.nickname())) {
            throw new CustomException(ErrorCode.AUTH_007);
        }
        if (!request.password().equals(request.passwordConfirm())) {
            throw new CustomException(ErrorCode.AUTH_008);
        }

        Member member = new Member(
                email,
                passwordEncoder.encode(request.password()),
                request.nickname(),
                request.toConsent()
        );
        try {
            memberRepository.saveWithNicknameUniqueness(member);
        } catch (DataIntegrityViolationException exception) {
            // 소셜 가입은 같은 위반을 AUTH_012로 다뤄야 해서 공용 저장 메서드가 아닌 여기서 매핑한다.
            if (ConstraintViolations.nameOf(exception).filter("uk_member_email"::equalsIgnoreCase).isPresent()) {
                throw new CustomException(ErrorCode.AUTH_006);
            }
            throw exception;
        }
        // 삭제에 실패해도 토큰은 30분 뒤 만료되고, 같은 이메일 재가입은 AUTH_006으로 막히므로 가입 성공을 뒤집지 않는다.
        AfterCommit.runBestEffort("가입 인증 토큰 삭제", () -> verificationTokenRepository.delete(
                EmailCodeService.VERIFIED_EMAIL_PURPOSE, request.verificationToken()));
    }

    public boolean isNicknameAvailable(String nickname) {
        return !memberRepository.isNicknameTaken(nickname);
    }

    /**
     * 시도 횟수는 비밀번호 검사 전에 예약해서 센다(동시 요청으로 한도를 넘기지 못하게). 잠금 단위와
     * 이유는 {@link LoginAttemptRecorder} 참고. 트랜잭션 없이 실행한다 — DB 접근은 닉네임 조회 한 번뿐인데
     * 트랜잭션을 잡으면 bcrypt 검사와 Redis 호출 동안 커넥션을 붙잡아, 대입 공격이 몰릴 때 커넥션 풀이 마른다.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public TokenResponse login(String clientIp, LoginRequest request) {
        // 탈퇴한 회원은 닉네임이 "탈퇴한회원N"으로 바뀌어 있지만, 그 이름으로 시도해도 없는 아이디와 같이 응답한다
        Member member = memberRepository.findByNickname(request.nickname())
                .filter(found -> !found.isWithdrawn())
                .orElseThrow(() -> new CustomException(ErrorCode.AUTH_001));

        if (!loginAttemptRecorder.tryAcquire(member.getId(), clientIp)) {
            throw new CustomException(ErrorCode.AUTH_010);
        }

        // 소셜 전용 계정도 계정 존재 여부가 드러나지 않도록 비밀번호 불일치와 동일하게 처리 (시도는 이미 셈)
        if (!member.hasPassword() || !passwordEncoder.matches(request.password(), member.getPassword())) {
            throw new CustomException(ErrorCode.AUTH_001);
        }

        loginAttemptRecorder.recordSuccess(member.getId(), clientIp);
        return tokenService.issueTokens(member.getId());
    }

    /** 비밀번호 재설정/계정 잠금 해제 메일이 공유하는 템플릿(verification-link.html) 렌더링 헬퍼. */
    private void publishVerificationLinkEmail(
            String to, String subject, String title, String message, String link, String buttonText
    ) {
        String html = emailTemplateRenderer.render("verification-link", Map.of(
                "title", title,
                "message", message,
                "link", link,
                "buttonText", buttonText,
                "ttlMinutes", VERIFICATION_TOKEN_TTL.toMinutes(),
                "logoUrl", logoUrl
        ));
        String plainText = title + "\n\n" + message + "\n\n" + link
                + "\n\n(" + VERIFICATION_TOKEN_TTL.toMinutes() + "분간 유효합니다)";
        eventPublisher.publishEvent(new VerificationLinkIssuedEvent(to, subject, plainText, html));
    }

    /**
     * rate limit 체크를 트랜잭션 밖에서 먼저 끝낸다 — 클래스 기본값(readOnly 트랜잭션)을 그대로 두면
     * 거부되는 요청도 메서드 진입과 동시에 DB 커넥션을 잡았다 놓게 되어, 정작 rate limiter가
     * 필요한 부하 상황에서 "저렴하게 거부"라는 목적을 못 이룬다.
     * (같은 클래스 내 @Transactional 메서드를 this로 호출하면 프록시를 안 타므로 TransactionTemplate을 사용)
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void requestPasswordReset(String clientIp, String email) {
        if (!rateLimiter.isAllowed(PASSWORD_RESET_PURPOSE, clientIp, email)) {
            return;
        }
        transactionTemplate.executeWithoutResult(status ->
                // 소셜 전용 계정은 비밀번호가 없으므로 재설정 대상에서 제외 (계정 존재 여부가 드러나지 않도록 조용히 무시)
                memberRepository.findByEmail(email)
                        .filter(Member::hasPassword)
                        .ifPresent(member -> {
                            String token = verificationTokenRepository.issue(PASSWORD_RESET_PURPOSE, member.getId(), VERIFICATION_TOKEN_TTL);
                            String link = frontendUrl + "/reset-password?token=" + token;
                            publishVerificationLinkEmail(
                                    member.getEmail(),
                                    "비밀번호 재설정 안내",
                                    "비밀번호 재설정",
                                    "아래 버튼을 눌러 비밀번호를 재설정해주세요.",
                                    link,
                                    "비밀번호 재설정하기"
                            );
                        }));
    }

    @Transactional
    public void resetPassword(String token, String newPassword, String newPasswordConfirm) {
        if (!newPassword.equals(newPasswordConfirm)) {
            throw new CustomException(ErrorCode.AUTH_008);
        }

        Long memberId = verificationTokenRepository.consume(PASSWORD_RESET_PURPOSE, token)
                .orElseThrow(() -> new CustomException(ErrorCode.AUTH_011));

        Member member = memberRepository.findActiveByIdForUpdate(memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.AUTH_002));

        if (!member.hasPassword()) {
            throw new CustomException(ErrorCode.AUTH_011);
        }

        member.changePassword(passwordEncoder.encode(newPassword));
        // best-effort: 실패해도 뒤이은 세션 폐기(AllSessionsRevokedEvent)는 반드시 실행돼야 한다. 잠금은 15분 뒤 자연 해제.
        AfterCommit.runBestEffort("로그인 실패 기록 삭제", () -> loginAttemptRecorder.clearAll(memberId));
        eventPublisher.publishEvent(new AllSessionsRevokedEvent(memberId));
    }

    /**
     * 가입한 이메일로 아이디(닉네임)를 보내준다. 계정 존재 여부를 노출하지 않도록 결과와 무관하게 조용히 끝난다.
     * 소셜 전용 계정은 아이디로 로그인할 수 없으므로 아이디 대신 소셜 로그인 안내를 보낸다.
     * rate limit 체크를 트랜잭션 밖에서 먼저 끝내는 이유는 {@link #requestPasswordReset} 참고.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void requestLoginIdReminder(String clientIp, String email) {
        // 이메일당 하루 한도는 두지 않는다 — 제3자가 남의 이메일로 한도를 채우면 그 사람이 하루 동안 아이디 안내를
        // 못 받게 되어, 막으려던 메일 남용보다 피해가 크다. 비밀번호 재설정/잠금 해제와 같은 10분 한도만 적용.
        if (!rateLimiter.isAllowed(LOGIN_ID_FIND_PURPOSE, clientIp, email)) {
            return;
        }
        transactionTemplate.executeWithoutResult(status ->
                memberRepository.findByEmail(email).ifPresent(this::publishLoginIdEmail));
    }

    private void publishLoginIdEmail(Member member) {
        String message = loginIdMessage(member);
        Map<String, Object> variables = new HashMap<>(Map.of(
                "message", message,
                "hasPassword", member.hasPassword(),
                "logoUrl", logoUrl
        ));
        // 소셜 전용 계정의 닉네임은 로그인 아이디가 아니므로 템플릿에 아예 넘기지 않는다 (템플릿 조건에만 기대지 않도록)
        if (member.hasPassword()) {
            variables.put("loginId", member.getNickname());
        }
        String html = emailTemplateRenderer.render("login-id", variables);
        String plainText = "아이디 안내\n\n" + message
                + (member.hasPassword() ? "\n\n아이디: " + member.getNickname() : "");
        eventPublisher.publishEvent(new VerificationLinkIssuedEvent(member.getEmail(), "아이디 안내", plainText, html));
    }

    /** 소셜 전용 계정은 아이디로 로그인할 수 없으므로, 실제로 연결된 소셜 로그인 수단을 알려준다. */
    private String loginIdMessage(Member member) {
        if (member.hasPassword()) {
            return "요청하신 계정의 아이디입니다. 이 아이디와 비밀번호로 로그인해주세요.";
        }
        // LOCAL은 소셜 수단이 아니므로 걸러낸다. 예외로 처리하면 해당 계정만 500이 나 가입 여부가 드러난다.
        List<AuthProvider> providers = socialConnectionRepository.findByMember_Id(member.getId()).stream()
                .map(SocialConnection::getProvider)
                .filter(provider -> provider != AuthProvider.LOCAL)
                .sorted()
                .toList();
        if (providers.isEmpty() && member.getProvider() != AuthProvider.LOCAL) {
            // SocialConnection 백필 전/실패한 기존 소셜 회원은 가입 시 provider로 안내한다
            providers = List.of(member.getProvider());
        }
        if (providers.isEmpty()) {
            return "이 이메일은 소셜 로그인으로 가입된 계정입니다. 가입하신 소셜 계정으로 로그인해주세요.";
        }
        String names = providers.stream().map(AuthProvider::getDisplayName).collect(Collectors.joining("·"));
        return "이 이메일은 " + names + " 로그인으로 가입된 계정입니다. 해당 소셜 계정으로 로그인해주세요.";
    }

    /** rate limit 체크를 트랜잭션 밖에서 먼저 끝내는 이유는 {@link #requestPasswordReset} 참고. */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void requestAccountUnlock(String clientIp, String email) {
        if (!rateLimiter.isAllowed(ACCOUNT_UNLOCK_PURPOSE, clientIp, email)) {
            return;
        }
        transactionTemplate.executeWithoutResult(status ->
                memberRepository.findByEmail(email).ifPresent(member -> {
                    String token = verificationTokenRepository.issue(ACCOUNT_UNLOCK_PURPOSE, member.getId(), VERIFICATION_TOKEN_TTL);
                    String link = frontendUrl + "/unlock-account?token=" + token;
                    publishVerificationLinkEmail(
                            member.getEmail(),
                            "계정 잠금 해제 안내",
                            "계정 잠금 해제",
                            "아래 버튼을 눌러 계정 잠금을 해제해주세요.",
                            link,
                            "잠금 해제하기"
                    );
                }));
    }

    @Transactional
    public void unlockAccount(String token) {
        Long memberId = verificationTokenRepository.consume(ACCOUNT_UNLOCK_PURPOSE, token)
                .orElseThrow(() -> new CustomException(ErrorCode.AUTH_011));

        if (!memberRepository.existsActiveById(memberId)) {
            throw new CustomException(ErrorCode.AUTH_002);
        }

        AfterCommit.runBestEffort("로그인 실패 기록 삭제", () -> loginAttemptRecorder.clearAll(memberId));
        eventPublisher.publishEvent(new AllSessionsRevokedEvent(memberId));
    }
}
