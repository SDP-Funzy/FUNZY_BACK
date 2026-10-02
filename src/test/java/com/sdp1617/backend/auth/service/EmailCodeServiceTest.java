package com.sdp1617.backend.auth.service;

import com.sdp1617.backend.auth.email.EmailSender;
import com.sdp1617.backend.auth.email.EmailTemplateRenderer;
import com.sdp1617.backend.auth.repository.EmailCodeRepository;
import com.sdp1617.backend.auth.repository.EmailCodeRepository.VerifyResult;
import com.sdp1617.backend.auth.repository.MemberRepository;
import com.sdp1617.backend.auth.repository.VerificationTokenRepository;
import com.sdp1617.backend.global.error.CustomException;
import com.sdp1617.backend.global.error.ErrorCode;
import java.lang.reflect.Field;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailCodeServiceTest {

    private static final String EMAIL = "test@sdp1617.com";
    private static final String IP = "127.0.0.1";

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private EmailCodeRepository emailCodeRepository;

    @Mock
    private VerificationTokenRepository verificationTokenRepository;

    @Mock
    private VerificationRequestRateLimiter rateLimiter;

    @Mock
    private EmailSender emailSender;

    @Mock
    private EmailTemplateRenderer emailTemplateRenderer;

    @InjectMocks
    private EmailCodeService emailCodeService;

    @BeforeEach
    void setUp() throws Exception {
        Field logoUrlField = EmailCodeService.class.getDeclaredField("logoUrl");
        logoUrlField.setAccessible(true);
        logoUrlField.set(emailCodeService, "https://example.com/logo.png");

        lenient().when(emailTemplateRenderer.render(anyString(), anyMap())).thenReturn("<html></html>");
        lenient().when(emailCodeRepository.tryStartCooldown(anyString(), any())).thenReturn(true);
        lenient().when(rateLimiter.isAllowed(anyString(), anyString(), anyString())).thenReturn(true);
        lenient().when(rateLimiter.isAllowedDaily(anyString(), anyString())).thenReturn(true);
        lenient().when(rateLimiter.tryReserveForIp(anyString(), anyString())).thenReturn(true);
    }

    @Test
    void 인증번호_발송시_6자리_번호를_저장하고_같은_번호를_메일로_보낸다() {
        emailCodeService.sendCode(IP, EMAIL);

        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailCodeRepository).saveCode(eq(EMAIL), codeCaptor.capture(), eq(EmailCodeService.CODE_TTL));
        String code = codeCaptor.getValue();
        assertTrue(code.matches("\\d{6}"));

        ArgumentCaptor<String> plainTextCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailSender).send(eq(EMAIL), anyString(), plainTextCaptor.capture(), anyString());
        assertTrue(plainTextCaptor.getValue().contains(code));
    }

    @Test
    void 이미_가입된_이메일이면_AUTH_006_예외를_던지고_발송하지_않는다() {
        when(memberRepository.existsByEmail(EMAIL)).thenReturn(true);

        CustomException exception = assertThrows(CustomException.class, () -> emailCodeService.sendCode(IP, EMAIL));

        assertEquals(ErrorCode.AUTH_006, exception.getErrorCode());
        verify(emailSender, never()).send(any(), any(), any(), any());
    }

    @Test
    void 가입_여부_확인보다_요청_제한을_먼저_적용한다() {
        when(rateLimiter.isAllowed(EmailCodeService.SEND_RATE_LIMIT_PURPOSE, IP, EMAIL)).thenReturn(false);

        assertThrows(CustomException.class, () -> emailCodeService.sendCode(IP, EMAIL));

        verify(memberRepository, never()).existsByEmail(any());
    }

    @Test
    void 메일_발송에_실패하면_새_인증번호를_저장하지_않고_쿨다운을_해제한다() {
        MailSendException failure = new MailSendException("smtp down");
        doThrow(failure).when(emailSender).send(eq(EMAIL), anyString(), anyString(), anyString());

        MailSendException thrown = assertThrows(MailSendException.class, () -> emailCodeService.sendCode(IP, EMAIL));

        assertEquals(failure, thrown);
        // 저장하지 않아야 사용자가 직전에 받은 인증번호를 계속 쓸 수 있다
        verify(emailCodeRepository, never()).saveCode(any(), any(), any());
        verify(emailCodeRepository).clearCooldown(EMAIL);
    }

    @Test
    void 메일_렌더링에_실패하면_이전_인증번호를_덮어쓰지_않고_쿨다운을_해제한다() {
        when(emailTemplateRenderer.render(anyString(), anyMap())).thenThrow(new IllegalStateException("template error"));

        assertThrows(IllegalStateException.class, () -> emailCodeService.sendCode(IP, EMAIL));

        verify(emailCodeRepository, never()).saveCode(any(), any(), any());
        verify(emailCodeRepository).clearCooldown(EMAIL);
    }

    @Test
    void 재발송_쿨다운_중이면_AUTH_024_예외를_던진다() {
        when(emailCodeRepository.tryStartCooldown(EMAIL, EmailCodeService.RESEND_COOLDOWN)).thenReturn(false);

        CustomException exception = assertThrows(CustomException.class, () -> emailCodeService.sendCode(IP, EMAIL));

        assertEquals(ErrorCode.AUTH_024, exception.getErrorCode());
        verify(emailSender, never()).send(any(), any(), any(), any());
    }

    @Test
    void rate_limit을_초과하면_AUTH_025_예외를_던진다() {
        when(rateLimiter.isAllowed(EmailCodeService.SEND_RATE_LIMIT_PURPOSE, IP, EMAIL)).thenReturn(false);

        CustomException exception = assertThrows(CustomException.class, () -> emailCodeService.sendCode(IP, EMAIL));

        assertEquals(ErrorCode.AUTH_025, exception.getErrorCode());
        verify(emailCodeRepository, never()).saveCode(any(), any(), any());
        // 한도에 걸린 요청은 쿨다운을 남기지 않는다 (다음 요청이 AUTH_024를 받지 않도록)
        verify(emailCodeRepository).clearCooldown(EMAIL);
        verify(emailSender, never()).send(any(), any(), any(), any());
    }

    @Test
    void 인증번호가_맞으면_인증_완료_토큰을_발급한다() {
        when(emailCodeRepository.verify(EMAIL, "123456", EmailCodeService.MAX_VERIFY_ATTEMPTS))
                .thenReturn(VerifyResult.MATCHED);
        when(verificationTokenRepository.issueValue(
                EmailCodeService.VERIFIED_EMAIL_PURPOSE, EMAIL, EmailCodeService.VERIFIED_TOKEN_TTL))
                .thenReturn("verified-token");

        assertEquals("verified-token", emailCodeService.verifyCode(IP, EMAIL, "123456"));
        // 성공한 확인은 예약을 돌려줘 IP 한도에 세지 않는다 (같은 IP를 쓰는 정상 사용자끼리 막히지 않도록)
        verify(rateLimiter).releaseForIp(EmailCodeService.VERIFY_RATE_LIMIT_PURPOSE, IP);
    }

    @Test
    void 인증번호_확인_요청이_IP_제한을_넘으면_AUTH_025_예외를_던지고_비교하지_않는다() {
        when(rateLimiter.tryReserveForIp(EmailCodeService.VERIFY_RATE_LIMIT_PURPOSE, IP)).thenReturn(false);

        CustomException exception = assertThrows(CustomException.class,
                () -> emailCodeService.verifyCode(IP, EMAIL, "123456"));

        assertEquals(ErrorCode.AUTH_025, exception.getErrorCode());
        verify(emailCodeRepository, never()).verify(any(), any(), anyInt());
    }

    @Test
    void 인증번호가_틀리면_AUTH_021_예외를_던진다() {
        assertVerifyFails(VerifyResult.MISMATCHED, ErrorCode.AUTH_021);
    }

    @Test
    void 인증번호가_만료되었으면_AUTH_022_예외를_던지되_IP_실패로는_세지_않는다() {
        assertVerifyFails(VerifyResult.EXPIRED, ErrorCode.AUTH_022, false);
    }

    @Test
    void 인증번호_시도_횟수를_초과하면_AUTH_023_예외를_던진다() {
        assertVerifyFails(VerifyResult.ATTEMPTS_EXCEEDED, ErrorCode.AUTH_023);
    }

    private void assertVerifyFails(VerifyResult result, ErrorCode expected) {
        assertVerifyFails(result, expected, true);
    }

    private void assertVerifyFails(VerifyResult result, ErrorCode expected, boolean countedAsIpFailure) {
        when(emailCodeRepository.verify(EMAIL, "000000", EmailCodeService.MAX_VERIFY_ATTEMPTS)).thenReturn(result);

        CustomException exception = assertThrows(CustomException.class,
                () -> emailCodeService.verifyCode(IP, EMAIL, "000000"));

        assertEquals(expected, exception.getErrorCode());
        verify(verificationTokenRepository, never()).issueValue(any(), any(), any());
        verify(rateLimiter).tryReserveForIp(EmailCodeService.VERIFY_RATE_LIMIT_PURPOSE, IP);
        if (countedAsIpFailure) {
            verify(rateLimiter, never()).releaseForIp(anyString(), anyString());
        } else {
            verify(rateLimiter).releaseForIp(EmailCodeService.VERIFY_RATE_LIMIT_PURPOSE, IP);
        }
    }

    @Test
    void 이메일당_하루_한도를_넘으면_AUTH_025_예외를_던지고_쿨다운을_해제한다() {
        when(rateLimiter.isAllowedDaily(EmailCodeService.SEND_RATE_LIMIT_PURPOSE, EMAIL)).thenReturn(false);

        CustomException exception = assertThrows(CustomException.class, () -> emailCodeService.sendCode(IP, EMAIL));

        assertEquals(ErrorCode.AUTH_025, exception.getErrorCode());
        verify(emailSender, never()).send(any(), any(), any(), any());
        verify(emailCodeRepository).clearCooldown(EMAIL);
    }

    @Test
    void 인증번호_확인_중_오류가_나면_IP_예약을_돌려주고_예외를_그대로_던진다() {
        IllegalStateException failure = new IllegalStateException("redis down");
        when(emailCodeRepository.verify(EMAIL, "123456", EmailCodeService.MAX_VERIFY_ATTEMPTS)).thenThrow(failure);

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> emailCodeService.verifyCode(IP, EMAIL, "123456"));

        assertEquals(failure, thrown);
        verify(rateLimiter).releaseForIp(EmailCodeService.VERIFY_RATE_LIMIT_PURPOSE, IP);
    }
}
