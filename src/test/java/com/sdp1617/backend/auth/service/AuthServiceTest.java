package com.sdp1617.backend.auth.service;

import com.sdp1617.backend.auth.dto.LoginRequest;
import com.sdp1617.backend.auth.dto.SignUpRequest;
import com.sdp1617.backend.auth.dto.TokenResponse;
import com.sdp1617.backend.auth.email.EmailTemplateRenderer;
import com.sdp1617.backend.auth.email.VerificationLinkIssuedEvent;
import com.sdp1617.backend.auth.entity.AuthProvider;
import com.sdp1617.backend.auth.entity.Consent;
import com.sdp1617.backend.auth.entity.Member;
import com.sdp1617.backend.auth.repository.MemberRepository;
import com.sdp1617.backend.auth.repository.VerificationTokenRepository;
import com.sdp1617.backend.global.error.CustomException;
import com.sdp1617.backend.global.error.ErrorCode;
import java.lang.reflect.Field;
import java.sql.SQLException;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String IP = "127.0.0.1";

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @Mock
    private TokenService tokenService;

    @Mock
    private LoginAttemptRecorder loginAttemptRecorder;

    @Mock
    private VerificationTokenRepository verificationTokenRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private EmailTemplateRenderer emailTemplateRenderer;

    @Mock
    private VerificationRequestRateLimiter rateLimiter;

    @Mock
    private TransactionTemplate transactionTemplate;

    @InjectMocks
    private AuthService authService;

    @BeforeEach
    void setUp() throws Exception {
        Field field = AuthService.class.getDeclaredField("frontendUrl");
        field.setAccessible(true);
        field.set(authService, "http://localhost:3000");

        Field logoUrlField = AuthService.class.getDeclaredField("logoUrl");
        logoUrlField.setAccessible(true);
        logoUrlField.set(authService, "https://sdp-funzy.s3.ap-northeast-2.amazonaws.com/static/funzy-logo.png");

        // 실제 렌더링은 EmailTemplateRendererTest에서 검증하므로, 여기선 link 값만 그대로 돌려준다.
        lenient().when(emailTemplateRenderer.render(anyString(), anyMap())).thenAnswer(invocation -> {
            Map<String, Object> variables = invocation.getArgument(1);
            return "link=" + variables.get("link");
        });

        lenient().when(verificationTokenRepository.findValue(EmailCodeService.VERIFIED_EMAIL_PURPOSE, "verified-token"))
                .thenReturn(Optional.of("test@sdp1617.com"));

        lenient().when(rateLimiter.isAllowed(anyString(), anyString(), anyString())).thenReturn(true);

        // 잠기지 않은 상태가 기본. 잠금 시나리오는 개별 테스트에서 덮어쓴다.
        lenient().when(loginAttemptRecorder.tryAcquire(any(), any())).thenReturn(true);

        // TransactionTemplate은 목이라 executeWithoutResult가 그냥 삼켜지므로, 전달받은 콜백을 직접 실행해준다.
        lenient().doAnswer(invocation -> {
            Consumer<TransactionStatus> action = invocation.getArgument(0);
            action.accept(new SimpleTransactionStatus());
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
    }

    private Member member(String email, String password, String nickname) {
        return new Member(email, password, nickname, Consent.requiredOnly());
    }

    @Test
    void 이메일이_중복되면_AUTH_006_예외를_던진다() {
        when(memberRepository.existsByEmail("test@sdp1617.com")).thenReturn(true);

        SignUpRequest request = new SignUpRequest("verified-token", "Password1!", "Password1!", "닉네임", true, true, false, false);

        CustomException exception = assertThrows(CustomException.class, () -> authService.signUp(request));

        assertEquals(ErrorCode.AUTH_006, exception.getErrorCode());
        verify(memberRepository, never()).saveWithNicknameUniqueness(any());
    }

    @Test
    void 닉네임이_중복되면_AUTH_007_예외를_던진다() {
        when(memberRepository.existsByEmail(anyString())).thenReturn(false);
        when(memberRepository.existsByNickname("닉네임")).thenReturn(true);

        SignUpRequest request = new SignUpRequest("verified-token", "Password1!", "Password1!", "닉네임", true, true, false, false);

        CustomException exception = assertThrows(CustomException.class, () -> authService.signUp(request));

        assertEquals(ErrorCode.AUTH_007, exception.getErrorCode());
    }

    @Test
    void 비밀번호와_비밀번호확인이_다르면_AUTH_008_예외를_던진다() {
        when(memberRepository.existsByEmail(anyString())).thenReturn(false);
        when(memberRepository.existsByNickname(anyString())).thenReturn(false);

        SignUpRequest request = new SignUpRequest("verified-token", "Password1!", "Password2!", "닉네임", true, true, false, false);

        CustomException exception = assertThrows(CustomException.class, () -> authService.signUp(request));

        assertEquals(ErrorCode.AUTH_008, exception.getErrorCode());
    }

    @Test
    void 정상_회원가입시_비밀번호를_암호화해서_저장한다() {
        when(memberRepository.existsByEmail(anyString())).thenReturn(false);
        when(memberRepository.existsByNickname(anyString())).thenReturn(false);
        when(passwordEncoder.encode("Password1!")).thenReturn("encoded-password");

        SignUpRequest request = new SignUpRequest("verified-token", "Password1!", "Password1!", "닉네임", true, true, false, false);
        authService.signUp(request);

        ArgumentCaptor<Member> captor = ArgumentCaptor.forClass(Member.class);
        verify(memberRepository).saveWithNicknameUniqueness(captor.capture());
        assertEquals("encoded-password", captor.getValue().getPassword());
    }

    @Test
    void 정상_회원가입시_요청의_동의항목이_회원에_그대로_반영된다() {
        when(memberRepository.existsByEmail(anyString())).thenReturn(false);
        when(memberRepository.existsByNickname(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("encoded-password");

        SignUpRequest request = new SignUpRequest(
                "verified-token", "Password1!", "Password1!", "닉네임", true, true, true, false);
        authService.signUp(request);

        ArgumentCaptor<Member> captor = ArgumentCaptor.forClass(Member.class);
        verify(memberRepository).saveWithNicknameUniqueness(captor.capture());
        assertEquals(request.toConsent(), captor.getValue().getConsent());
    }

    @Test
    void 정상_회원가입시_인증_토큰의_이메일로_계정을_만들고_토큰을_폐기한다() {
        when(memberRepository.existsByEmail(anyString())).thenReturn(false);
        when(memberRepository.existsByNickname(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("encoded-password");

        SignUpRequest request = new SignUpRequest("verified-token", "Password1!", "Password1!", "닉네임", true, true, false, false);
        authService.signUp(request);

        ArgumentCaptor<Member> captor = ArgumentCaptor.forClass(Member.class);
        verify(memberRepository).saveWithNicknameUniqueness(captor.capture());
        assertEquals("test@sdp1617.com", captor.getValue().getEmail());
        verify(verificationTokenRepository).delete(EmailCodeService.VERIFIED_EMAIL_PURPOSE, "verified-token");
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void 인증_토큰이_없거나_만료되었으면_AUTH_026_예외를_던진다() {
        when(verificationTokenRepository.findValue(EmailCodeService.VERIFIED_EMAIL_PURPOSE, "expired-token"))
                .thenReturn(Optional.empty());

        SignUpRequest request = new SignUpRequest("expired-token", "Password1!", "Password1!", "닉네임", true, true, false, false);

        CustomException exception = assertThrows(CustomException.class, () -> authService.signUp(request));

        assertEquals(ErrorCode.AUTH_026, exception.getErrorCode());
        verify(memberRepository, never()).saveWithNicknameUniqueness(any());
    }

    @Test
    void 가입이_실패하면_인증_토큰을_폐기하지_않는다() {
        when(memberRepository.existsByEmail(anyString())).thenReturn(false);
        when(memberRepository.existsByNickname("닉네임")).thenReturn(true);

        SignUpRequest request = new SignUpRequest("verified-token", "Password1!", "Password1!", "닉네임", true, true, false, false);

        assertThrows(CustomException.class, () -> authService.signUp(request));

        verify(verificationTokenRepository, never()).delete(any(), any());
    }

    @Test
    void 같은_토큰으로_동시에_가입해_이메일_유니크_제약에_걸리면_AUTH_006_예외를_던진다() {
        when(memberRepository.existsByEmail(anyString())).thenReturn(false);
        when(memberRepository.existsByNickname(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("encoded-password");
        when(memberRepository.saveWithNicknameUniqueness(any())).thenThrow(new DataIntegrityViolationException(
                "unique constraint", new ConstraintViolationException(
                        "constraint violated", new SQLException("duplicate key"), "uk_member_email")));

        SignUpRequest request = new SignUpRequest("verified-token", "Password1!", "Password1!", "닉네임", true, true, false, false);

        CustomException exception = assertThrows(CustomException.class, () -> authService.signUp(request));

        assertEquals(ErrorCode.AUTH_006, exception.getErrorCode());
        verify(verificationTokenRepository, never()).delete(any(), any());
    }

    @Test
    void 존재하지_않는_아이디로_로그인하면_AUTH_001_예외를_던진다() {
        when(memberRepository.findByNickname("없는아이디")).thenReturn(Optional.empty());

        LoginRequest request = new LoginRequest("없는아이디", "Password1!");

        CustomException exception = assertThrows(CustomException.class, () -> authService.login(IP, request));

        assertEquals(ErrorCode.AUTH_001, exception.getErrorCode());
    }

    @Test
    void 요청_IP에서_잠긴_계정으로_로그인하면_비밀번호_확인_없이_AUTH_010_예외를_던진다() {
        Member member = member("test@sdp1617.com", "encoded", "닉네임");
        setId(member, 1L);
        when(memberRepository.findByNickname("닉네임")).thenReturn(Optional.of(member));
        when(loginAttemptRecorder.tryAcquire(1L, IP)).thenReturn(false);

        LoginRequest request = new LoginRequest("닉네임", "Password1!");

        CustomException exception = assertThrows(CustomException.class, () -> authService.login(IP, request));

        assertEquals(ErrorCode.AUTH_010, exception.getErrorCode());
        verify(passwordEncoder, never()).matches(any(), any());
        verify(loginAttemptRecorder, never()).recordSuccess(any(), any());
    }

    @Test
    void 소셜전용_계정으로_아이디_로그인을_시도하면_AUTH_001_예외를_던진다() {
        Member member = new Member("social@sdp1617.com", "닉네임", Consent.requiredOnly(), AuthProvider.KAKAO, "12345");
        setId(member, 1L);
        when(memberRepository.findByNickname("닉네임")).thenReturn(Optional.of(member));

        LoginRequest request = new LoginRequest("닉네임", "Password1!");

        CustomException exception = assertThrows(CustomException.class, () -> authService.login(IP, request));

        assertEquals(ErrorCode.AUTH_001, exception.getErrorCode());
        verify(loginAttemptRecorder).tryAcquire(1L, IP);
        verify(loginAttemptRecorder, never()).recordSuccess(any(), any());
    }

    @Test
    void 비밀번호가_틀리면_예약된_시도를_그대로_남기고_AUTH_001_예외를_던진다() {
        Member member = member("test@sdp1617.com", "encoded", "닉네임");
        setId(member, 1L);
        when(memberRepository.findByNickname("닉네임")).thenReturn(Optional.of(member));
        when(passwordEncoder.matches("wrong", "encoded")).thenReturn(false);

        LoginRequest request = new LoginRequest("닉네임", "wrong");

        CustomException exception = assertThrows(CustomException.class, () -> authService.login(IP, request));

        assertEquals(ErrorCode.AUTH_001, exception.getErrorCode());
        verify(loginAttemptRecorder).tryAcquire(1L, IP);
        verify(loginAttemptRecorder, never()).recordSuccess(any(), any());
    }

    @Test
    void 로그인_성공시_그_IP의_실패기록을_지우고_토큰을_발급한다() {
        Member member = member("test@sdp1617.com", "encoded", "닉네임");
        setId(member, 1L);
        when(memberRepository.findByNickname("닉네임")).thenReturn(Optional.of(member));
        when(passwordEncoder.matches("Password1!", "encoded")).thenReturn(true);
        when(tokenService.issueTokens(1L)).thenReturn(new TokenResponse("access", "refresh"));

        LoginRequest request = new LoginRequest("닉네임", "Password1!");
        TokenResponse response = authService.login(IP, request);

        assertEquals("access", response.accessToken());
        verify(loginAttemptRecorder).recordSuccess(1L, IP);
    }

    @Test
    void 유효한_토큰으로_비밀번호를_재설정하면_잠금도_풀리고_모든_세션이_폐기된다() {
        Member member = member("test@sdp1617.com", "old-encoded", "닉네임");
        when(verificationTokenRepository.consume("password-reset", "token-value")).thenReturn(Optional.of(1L));
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
        when(passwordEncoder.encode("NewPassword1!")).thenReturn("new-encoded");

        authService.resetPassword("token-value", "NewPassword1!", "NewPassword1!");

        assertEquals("new-encoded", member.getPassword());
        verify(loginAttemptRecorder).clearAll(1L);
        verify(eventPublisher).publishEvent(new AllSessionsRevokedEvent(1L));
    }

    @Test
    void 만료되거나_잘못된_토큰으로_비밀번호_재설정시_AUTH_011_예외를_던진다() {
        when(verificationTokenRepository.consume("password-reset", "bad-token")).thenReturn(Optional.empty());

        CustomException exception = assertThrows(CustomException.class,
                () -> authService.resetPassword("bad-token", "NewPassword1!", "NewPassword1!"));

        assertEquals(ErrorCode.AUTH_011, exception.getErrorCode());
    }

    @Test
    void 비밀번호_재설정_요청시_링크가_포함된_이벤트를_발행한다() {
        Member member = member("test@sdp1617.com", "encoded", "닉네임");
        setId(member, 1L);
        when(memberRepository.findByEmail("test@sdp1617.com")).thenReturn(Optional.of(member));
        when(verificationTokenRepository.issue("password-reset", 1L, Duration.ofMinutes(15))).thenReturn("token-value");

        authService.requestPasswordReset("127.0.0.1", "test@sdp1617.com");

        ArgumentCaptor<VerificationLinkIssuedEvent> captor = ArgumentCaptor.forClass(VerificationLinkIssuedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertEquals("test@sdp1617.com", captor.getValue().to());
        assertTrue(captor.getValue().plainText().contains("http://localhost:3000/reset-password?token=token-value"));
    }

    @Test
    void 비밀번호_재설정_요청시_소셜_전용_계정이면_조용히_무시한다() {
        Member member = new Member("social@sdp1617.com", "닉네임", Consent.requiredOnly(), AuthProvider.KAKAO, "12345");
        setId(member, 1L);
        when(memberRepository.findByEmail("social@sdp1617.com")).thenReturn(Optional.of(member));

        authService.requestPasswordReset("127.0.0.1", "social@sdp1617.com");

        verify(eventPublisher, never()).publishEvent(any());
        verify(verificationTokenRepository, never()).issue(any(), any(), any());
    }

    @Test
    void 소셜_전용_계정이_비밀번호_재설정_토큰을_가지고_있어도_AUTH_011_예외를_던진다() {
        Member member = new Member("social@sdp1617.com", "닉네임", Consent.requiredOnly(), AuthProvider.KAKAO, "12345");
        setId(member, 1L);
        when(verificationTokenRepository.consume("password-reset", "token-value")).thenReturn(Optional.of(1L));
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));

        CustomException exception = assertThrows(CustomException.class,
                () -> authService.resetPassword("token-value", "NewPassword1!", "NewPassword1!"));

        assertEquals(ErrorCode.AUTH_011, exception.getErrorCode());
    }

    @Test
    void 비밀번호_재설정_요청시_존재하지_않는_이메일이면_조용히_무시한다() {
        when(memberRepository.findByEmail("nobody@sdp1617.com")).thenReturn(Optional.empty());

        authService.requestPasswordReset("127.0.0.1", "nobody@sdp1617.com");

        verify(eventPublisher, never()).publishEvent(any());
        verify(verificationTokenRepository, never()).issue(any(), any(), any());
    }

    @Test
    void 비밀번호_재설정_요청이_rate_limit에_걸리면_계정_조회_없이_조용히_무시한다() {
        when(rateLimiter.isAllowed("password-reset", "127.0.0.1", "test@sdp1617.com")).thenReturn(false);

        authService.requestPasswordReset("127.0.0.1", "test@sdp1617.com");

        verify(memberRepository, never()).findByEmail(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void 계정_잠금_해제_요청시_존재하지_않는_이메일이면_조용히_무시한다() {
        when(memberRepository.findByEmail("nobody@sdp1617.com")).thenReturn(Optional.empty());

        authService.requestAccountUnlock("127.0.0.1", "nobody@sdp1617.com");

        verify(eventPublisher, never()).publishEvent(any());
        verify(verificationTokenRepository, never()).issue(any(), any(), any());
    }

    @Test
    void 계정_잠금_해제_요청이_rate_limit에_걸리면_계정_조회_없이_조용히_무시한다() {
        when(rateLimiter.isAllowed("account-unlock", "127.0.0.1", "test@sdp1617.com")).thenReturn(false);

        authService.requestAccountUnlock("127.0.0.1", "test@sdp1617.com");

        verify(memberRepository, never()).findByEmail(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void 계정_잠금_해제시_모든_IP의_실패기록을_지우고_모든_세션을_폐기한다() {
        when(verificationTokenRepository.consume("account-unlock", "token-value")).thenReturn(Optional.of(1L));
        when(memberRepository.existsById(1L)).thenReturn(true);

        authService.unlockAccount("token-value");

        verify(loginAttemptRecorder).clearAll(1L);
        verify(eventPublisher).publishEvent(new AllSessionsRevokedEvent(1L));
    }

    @Test
    void 사용_가능한_닉네임이면_true를_반환한다() {
        when(memberRepository.existsByNickname("새닉네임")).thenReturn(false);

        assertTrue(authService.isNicknameAvailable("새닉네임"));
    }

    private void setId(Member member, Long id) {
        try {
            Field field = Member.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(member, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
