package com.sdp1617.backend.auth.service;

import com.sdp1617.backend.auth.dto.SocialAuthResponse;
import com.sdp1617.backend.auth.dto.TokenResponse;
import com.sdp1617.backend.auth.entity.AuthProvider;
import com.sdp1617.backend.auth.entity.Consent;
import com.sdp1617.backend.auth.entity.Member;
import com.sdp1617.backend.auth.entity.SocialConnection;
import com.sdp1617.backend.auth.repository.MemberRepository;
import com.sdp1617.backend.auth.repository.SocialConnectionRepository;
import com.sdp1617.backend.auth.repository.SocialSignupSessionRepository;
import com.sdp1617.backend.auth.social.SocialSignupSession;
import com.sdp1617.backend.auth.social.SocialUserInfo;
import com.sdp1617.backend.auth.social.SocialUserInfoProvider;
import com.sdp1617.backend.auth.social.SocialUserInfoProviderRegistry;
import com.sdp1617.backend.global.error.CustomException;
import com.sdp1617.backend.global.error.ErrorCode;
import java.lang.reflect.Field;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Optional;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SocialAuthServiceTest {

    @Mock
    private SocialUserInfoProviderRegistry providerRegistry;

    @Mock
    private SocialUserInfoProvider kakaoProvider;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private SocialConnectionRepository socialConnectionRepository;

    @Mock
    private SocialSignupSessionRepository socialSignupSessionRepository;

    @Mock
    private TokenService tokenService;

    @Mock
    private TransactionTemplate transactionTemplate;

    @InjectMocks
    private SocialAuthService socialAuthService;

    @BeforeEach
    void setUp() {
        // 실제 트랜잭션 없이, 전달받은 콜백을 그 자리에서 바로 실행해준다.
        lenient().doAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(new SimpleTransactionStatus());
        }).when(transactionTemplate).execute(any());
    }

    @Test
    void 기존_소셜계정이면_바로_토큰을_발급한다() {
        when(providerRegistry.get(AuthProvider.KAKAO)).thenReturn(kakaoProvider);
        when(kakaoProvider.fetchUserInfo("token")).thenReturn(new SocialUserInfo("12345", "test@kakao.com"));

        Member member = new Member("test@kakao.com", "닉네임", Consent.requiredOnly(), AuthProvider.KAKAO, "12345");
        setId(member, 1L);
        SocialConnection connection = SocialConnection.create(member, AuthProvider.KAKAO, "12345");
        when(socialConnectionRepository.findByProviderAndProviderId(AuthProvider.KAKAO, "12345"))
                .thenReturn(Optional.of(connection));
        when(tokenService.issueTokens(1L)).thenReturn(new TokenResponse("access", "refresh"));

        SocialAuthResponse response = socialAuthService.login(AuthProvider.KAKAO, "token");

        assertFalse(response.isNewUser());
        assertEquals("access", response.tokens().accessToken());
    }

    @Test
    void 신규_소셜유저면_signupToken을_발급한다() {
        when(providerRegistry.get(AuthProvider.KAKAO)).thenReturn(kakaoProvider);
        when(kakaoProvider.fetchUserInfo("token")).thenReturn(new SocialUserInfo("12345", "new@kakao.com"));
        when(socialConnectionRepository.findByProviderAndProviderId(AuthProvider.KAKAO, "12345")).thenReturn(Optional.empty());
        when(memberRepository.existsByEmail("new@kakao.com")).thenReturn(false);
        when(socialSignupSessionRepository.issue(any(), eq(Duration.ofMinutes(15)))).thenReturn("signup-token");

        SocialAuthResponse response = socialAuthService.login(AuthProvider.KAKAO, "token");

        assertTrue(response.isNewUser());
        assertEquals("signup-token", response.signupToken());
        assertEquals("new@kakao.com", response.email());
    }

    @Test
    void 이미_다른_방식으로_가입된_이메일이면_AUTH_012_예외를_던진다() {
        when(providerRegistry.get(AuthProvider.KAKAO)).thenReturn(kakaoProvider);
        when(kakaoProvider.fetchUserInfo("token")).thenReturn(new SocialUserInfo("12345", "existing@sdp1617.com"));
        when(socialConnectionRepository.findByProviderAndProviderId(AuthProvider.KAKAO, "12345")).thenReturn(Optional.empty());
        when(memberRepository.existsByEmail("existing@sdp1617.com")).thenReturn(true);

        CustomException exception = assertThrows(
                CustomException.class, () -> socialAuthService.login(AuthProvider.KAKAO, "token"));

        assertEquals(ErrorCode.AUTH_012, exception.getErrorCode());
    }

    @Test
    void 소셜_회원가입을_완료하면_토큰을_발급한다() {
        SocialSignupSession session = new SocialSignupSession(AuthProvider.KAKAO, "12345", "test@kakao.com");
        when(socialSignupSessionRepository.consume("signup-token")).thenReturn(Optional.of(session));
        when(memberRepository.existsByNickname("닉네임")).thenReturn(false);
        when(memberRepository.existsByEmail("test@kakao.com")).thenReturn(false);
        when(tokenService.issueTokens(any())).thenReturn(new TokenResponse("access", "refresh"));

        TokenResponse response = socialAuthService.completeSignUp("signup-token", "닉네임", Consent.requiredOnly());

        assertEquals("access", response.accessToken());
    }

    @Test
    void 소셜_회원가입을_완료하면_SocialConnection도_함께_생성된다() {
        SocialSignupSession session = new SocialSignupSession(AuthProvider.KAKAO, "12345", "test@kakao.com");
        when(socialSignupSessionRepository.consume("signup-token")).thenReturn(Optional.of(session));
        when(memberRepository.existsByNickname("닉네임")).thenReturn(false);
        when(memberRepository.existsByEmail("test@kakao.com")).thenReturn(false);
        when(tokenService.issueTokens(any())).thenReturn(new TokenResponse("access", "refresh"));

        socialAuthService.completeSignUp("signup-token", "닉네임", Consent.requiredOnly());

        ArgumentCaptor<Member> memberCaptor = ArgumentCaptor.forClass(Member.class);
        verify(memberRepository).saveWithNicknameUniqueness(memberCaptor.capture());

        ArgumentCaptor<SocialConnection> connectionCaptor = ArgumentCaptor.forClass(SocialConnection.class);
        verify(socialConnectionRepository).saveAndFlush(connectionCaptor.capture());
        assertEquals(AuthProvider.KAKAO, connectionCaptor.getValue().getProvider());
        assertEquals("12345", connectionCaptor.getValue().getProviderId());
        assertEquals(memberCaptor.getValue(), connectionCaptor.getValue().getMember());
    }

    @Test
    void 소셜_회원가입_완료시_전달받은_동의항목이_회원에_그대로_반영된다() {
        SocialSignupSession session = new SocialSignupSession(AuthProvider.KAKAO, "12345", "test@kakao.com");
        when(socialSignupSessionRepository.consume("signup-token")).thenReturn(Optional.of(session));
        when(memberRepository.existsByNickname("닉네임")).thenReturn(false);
        when(memberRepository.existsByEmail("test@kakao.com")).thenReturn(false);
        when(tokenService.issueTokens(any())).thenReturn(new TokenResponse("access", "refresh"));

        Consent consent = new Consent(true, true, false, true);
        socialAuthService.completeSignUp("signup-token", "닉네임", consent);

        ArgumentCaptor<Member> captor = ArgumentCaptor.forClass(Member.class);
        verify(memberRepository).saveWithNicknameUniqueness(captor.capture());
        assertEquals(consent, captor.getValue().getConsent());
    }

    @Test
    void 만료되거나_잘못된_signupToken이면_AUTH_011_예외를_던진다() {
        when(socialSignupSessionRepository.consume("bad-token")).thenReturn(Optional.empty());

        CustomException exception = assertThrows(CustomException.class,
                () -> socialAuthService.completeSignUp("bad-token", "닉네임", Consent.requiredOnly()));

        assertEquals(ErrorCode.AUTH_011, exception.getErrorCode());
    }

    @Test
    void 같은_소셜계정으로_동시에_회원가입을_완료하면_먼저_생성된_계정으로_로그인시킨다() {
        SocialSignupSession session = new SocialSignupSession(AuthProvider.KAKAO, "12345", "test@kakao.com");
        when(socialSignupSessionRepository.consume("signup-token")).thenReturn(Optional.of(session));
        when(memberRepository.existsByNickname("닉네임")).thenReturn(false);
        when(memberRepository.existsByEmail("test@kakao.com")).thenReturn(false);
        when(socialConnectionRepository.saveAndFlush(any()))
                .thenThrow(providerConnectionConstraintViolation());

        Member winner = new Member("test@kakao.com", "먼저가입", Consent.requiredOnly(), AuthProvider.KAKAO, "12345");
        setId(winner, 1L);
        SocialConnection existingConnection = SocialConnection.create(winner, AuthProvider.KAKAO, "12345");
        when(socialConnectionRepository.findByProviderAndProviderId(AuthProvider.KAKAO, "12345"))
                .thenReturn(Optional.of(existingConnection));
        when(tokenService.issueTokens(1L)).thenReturn(new TokenResponse("access", "refresh"));

        TokenResponse response = socialAuthService.completeSignUp("signup-token", "닉네임", Consent.requiredOnly());

        assertEquals("access", response.accessToken());
    }

    private DataIntegrityViolationException providerConnectionConstraintViolation() {
        ConstraintViolationException cause = new ConstraintViolationException(
                "duplicate key", new SQLException("duplicate"), "uk_social_connection_provider_provider_id");
        return new DataIntegrityViolationException("동시 가입 경쟁", cause);
    }

    @Test
    void 회원가입_완료_시_닉네임이_중복되면_AUTH_007_예외를_던진다() {
        SocialSignupSession session = new SocialSignupSession(AuthProvider.KAKAO, "12345", "test@kakao.com");
        when(socialSignupSessionRepository.consume("signup-token")).thenReturn(Optional.of(session));
        when(memberRepository.existsByNickname("닉네임")).thenReturn(true);

        CustomException exception = assertThrows(CustomException.class,
                () -> socialAuthService.completeSignUp("signup-token", "닉네임", Consent.requiredOnly()));

        assertEquals(ErrorCode.AUTH_007, exception.getErrorCode());
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
