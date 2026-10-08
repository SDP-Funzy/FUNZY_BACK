package com.sdp1617.backend.mypage.service;

import com.sdp1617.backend.auth.entity.AuthProvider;
import com.sdp1617.backend.auth.entity.Consent;
import com.sdp1617.backend.auth.entity.Member;
import com.sdp1617.backend.auth.entity.SocialConnection;
import com.sdp1617.backend.auth.repository.MemberRepository;
import com.sdp1617.backend.auth.repository.SocialConnectionRepository;
import com.sdp1617.backend.auth.service.AllSessionsRevokedEvent;
import com.sdp1617.backend.auth.service.TokenService;
import com.sdp1617.backend.auth.social.SocialUserInfo;
import com.sdp1617.backend.auth.social.SocialUserInfoProvider;
import com.sdp1617.backend.auth.social.SocialUserInfoProviderRegistry;
import com.sdp1617.backend.global.error.CustomException;
import com.sdp1617.backend.global.error.ErrorCode;
import com.sdp1617.backend.global.s3.S3ImageService;
import com.sdp1617.backend.mypage.dto.ConnectedAccountResponse;
import java.lang.reflect.Field;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountSettingsServiceTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private SocialConnectionRepository socialConnectionRepository;

    @Mock
    private SocialUserInfoProviderRegistry socialUserInfoProviderRegistry;

    @Mock
    private SocialUserInfoProvider kakaoProvider;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private TokenService tokenService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private TransactionTemplate transactionTemplate;

    @Mock
    private MemberWithdrawalCleaner memberWithdrawalCleaner;

    @Mock
    private S3ImageService s3ImageService;

    @InjectMocks
    private AccountSettingsService accountSettingsService;

    @BeforeEach
    void setUp() {
        // TransactionTemplate은 목이라 executeWithoutResult가 그냥 삼켜지므로, 전달받은 콜백을 직접 실행해준다.
        lenient().doAnswer(invocation -> {
            Consumer<TransactionStatus> action = invocation.getArgument(0);
            action.accept(new SimpleTransactionStatus());
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
    }

    private Member localMember() {
        return new Member("test@sdp1617.com", "encoded", "닉네임", Consent.requiredOnly());
    }

    private Member socialMember() {
        return new Member("social@sdp1617.com", "닉네임", Consent.requiredOnly(), AuthProvider.KAKAO, "12345");
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

    @Test
    void 연결_계정을_조회한다() {
        Member member = socialMember();
        setId(member, 1L);
        when(memberRepository.findActiveById(1L)).thenReturn(Optional.of(member));
        SocialConnection connection = SocialConnection.create(member, AuthProvider.KAKAO, "12345");
        when(socialConnectionRepository.findByMember_Id(1L)).thenReturn(List.of(connection));

        ConnectedAccountResponse response = accountSettingsService.getConnectedAccount(1L);

        assertEquals(List.of(AuthProvider.KAKAO), response.connectedProviders());
        assertFalse(response.hasPassword());
    }

    @Test
    void 비밀번호_변경에_성공하면_모든_세션을_폐기한다() {
        Member member = localMember();
        setId(member, 1L);
        when(memberRepository.findActiveByIdForUpdate(1L)).thenReturn(Optional.of(member));
        when(passwordEncoder.matches("oldPw1!", "encoded")).thenReturn(true);
        when(passwordEncoder.encode("NewPassword1!")).thenReturn("new-encoded");

        accountSettingsService.changePassword(1L, "oldPw1!", "NewPassword1!", "NewPassword1!");

        assertEquals("new-encoded", member.getPassword());
        verify(eventPublisher).publishEvent(new AllSessionsRevokedEvent(1L));
    }

    @Test
    void 소셜_전용_계정은_비밀번호_변경시_AUTH_014_예외를_던진다() {
        Member member = socialMember();
        setId(member, 1L);
        when(memberRepository.findActiveByIdForUpdate(1L)).thenReturn(Optional.of(member));

        CustomException exception = assertThrows(CustomException.class,
                () -> accountSettingsService.changePassword(1L, "oldPw1!", "NewPassword1!", "NewPassword1!"));

        assertEquals(ErrorCode.AUTH_014, exception.getErrorCode());
    }

    @Test
    void 현재_비밀번호가_틀리면_AUTH_015_예외를_던진다() {
        Member member = localMember();
        setId(member, 1L);
        when(memberRepository.findActiveByIdForUpdate(1L)).thenReturn(Optional.of(member));
        when(passwordEncoder.matches("wrong", "encoded")).thenReturn(false);

        CustomException exception = assertThrows(CustomException.class,
                () -> accountSettingsService.changePassword(1L, "wrong", "NewPassword1!", "NewPassword1!"));

        assertEquals(ErrorCode.AUTH_015, exception.getErrorCode());
    }

    @Test
    void 새_비밀번호와_확인이_다르면_AUTH_008_예외를_던진다() {
        Member member = localMember();
        setId(member, 1L);
        when(memberRepository.findActiveByIdForUpdate(1L)).thenReturn(Optional.of(member));
        when(passwordEncoder.matches("oldPw1!", "encoded")).thenReturn(true);

        CustomException exception = assertThrows(CustomException.class,
                () -> accountSettingsService.changePassword(1L, "oldPw1!", "NewPassword1!", "Different1!"));

        assertEquals(ErrorCode.AUTH_008, exception.getErrorCode());
    }

    @Test
    void 새_비밀번호가_현재_비밀번호와_같으면_AUTH_028_예외를_던지고_세션을_끊지_않는다() {
        Member member = localMember();
        setId(member, 1L);
        when(memberRepository.findActiveByIdForUpdate(1L)).thenReturn(Optional.of(member));
        when(passwordEncoder.matches("oldPw1!", "encoded")).thenReturn(true);

        CustomException exception = assertThrows(CustomException.class,
                () -> accountSettingsService.changePassword(1L, "oldPw1!", "oldPw1!", "oldPw1!"));

        assertEquals(ErrorCode.AUTH_028, exception.getErrorCode());
        assertEquals("encoded", member.getPassword());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void 로그아웃시_해당_세션만_폐기한다() {
        accountSettingsService.logout(1L, "refresh-token");

        verify(tokenService).revokeSession(1L, "refresh-token");
    }

    @Test
    void 회원_탈퇴시_계정을_익명화하고_연관_데이터를_정리하고_모든_세션을_폐기한다() {
        Member member = localMember();
        setId(member, 1L);
        when(memberRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(member));

        accountSettingsService.withdraw(1L);

        assertTrue(member.isWithdrawn());
        assertEquals(Member.WITHDRAWN_NICKNAME_PREFIX + 1L, member.getNickname());
        assertNull(member.getEmail());
        verify(memberRepository, never()).delete(any());
        verify(memberWithdrawalCleaner).clean(1L);
        verify(eventPublisher).publishEvent(new AllSessionsRevokedEvent(1L));
    }

    @Test
    void 이미_탈퇴한_회원이면_AUTH_002_예외를_던진다() {
        Member member = localMember();
        setId(member, 1L);
        member.withdraw();
        when(memberRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(member));

        CustomException exception = assertThrows(CustomException.class,
                () -> accountSettingsService.withdraw(1L));

        assertEquals(ErrorCode.AUTH_002, exception.getErrorCode());
        verify(memberWithdrawalCleaner, never()).clean(any());
    }

    @Test
    void 존재하지_않는_회원이면_AUTH_002_예외를_던진다() {
        when(memberRepository.findByIdForUpdate(1L)).thenReturn(Optional.empty());

        CustomException exception = assertThrows(CustomException.class,
                () -> accountSettingsService.withdraw(1L));

        assertEquals(ErrorCode.AUTH_002, exception.getErrorCode());
    }

    @Test
    void 소셜_계정을_정상적으로_연결한다() {
        Member member = localMember();
        setId(member, 1L);
        when(memberRepository.findActiveById(1L)).thenReturn(Optional.of(member));
        when(memberRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(member));
        when(socialConnectionRepository.existsByMember_IdAndProvider(1L, AuthProvider.KAKAO)).thenReturn(false);
        when(socialUserInfoProviderRegistry.get(AuthProvider.KAKAO)).thenReturn(kakaoProvider);
        when(kakaoProvider.fetchUserInfo("token")).thenReturn(new SocialUserInfo("12345", "social@kakao.com"));
        when(socialConnectionRepository.existsByProviderAndProviderId(AuthProvider.KAKAO, "12345")).thenReturn(false);

        accountSettingsService.connectSocialAccount(1L, AuthProvider.KAKAO, "token");

        verify(socialConnectionRepository).saveAndFlush(any());
    }

    @Test
    void 소셜_인증_중에_탈퇴가_끝났으면_연결하지_않는다() {
        Member member = localMember();
        setId(member, 1L);
        Member withdrawnMember = localMember();
        setId(withdrawnMember, 1L);
        withdrawnMember.withdraw();
        when(memberRepository.findActiveById(1L)).thenReturn(Optional.of(member));
        when(memberRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(withdrawnMember));
        when(socialConnectionRepository.existsByMember_IdAndProvider(1L, AuthProvider.KAKAO)).thenReturn(false);
        when(socialUserInfoProviderRegistry.get(AuthProvider.KAKAO)).thenReturn(kakaoProvider);
        when(kakaoProvider.fetchUserInfo("token")).thenReturn(new SocialUserInfo("12345", "social@kakao.com"));
        when(socialConnectionRepository.existsByProviderAndProviderId(AuthProvider.KAKAO, "12345")).thenReturn(false);

        CustomException exception = assertThrows(CustomException.class,
                () -> accountSettingsService.connectSocialAccount(1L, AuthProvider.KAKAO, "token"));

        assertEquals(ErrorCode.AUTH_002, exception.getErrorCode());
        verify(socialConnectionRepository, never()).saveAndFlush(any());
    }

    @Test
    void 이미_본인_계정에_연결된_provider면_외부_인증_호출_없이_AUTH_018_예외를_던진다() {
        Member member = localMember();
        setId(member, 1L);
        when(memberRepository.findActiveById(1L)).thenReturn(Optional.of(member));
        when(socialConnectionRepository.existsByMember_IdAndProvider(1L, AuthProvider.KAKAO)).thenReturn(true);

        CustomException exception = assertThrows(CustomException.class,
                () -> accountSettingsService.connectSocialAccount(1L, AuthProvider.KAKAO, "token"));

        assertEquals(ErrorCode.AUTH_018, exception.getErrorCode());
        verify(socialUserInfoProviderRegistry, never()).get(any());
    }

    @Test
    void 다른_계정에_이미_연결된_소셜계정이면_AUTH_017_예외를_던진다() {
        Member member = localMember();
        setId(member, 1L);
        when(memberRepository.findActiveById(1L)).thenReturn(Optional.of(member));
        when(socialConnectionRepository.existsByMember_IdAndProvider(1L, AuthProvider.KAKAO)).thenReturn(false);
        when(socialUserInfoProviderRegistry.get(AuthProvider.KAKAO)).thenReturn(kakaoProvider);
        when(kakaoProvider.fetchUserInfo("token")).thenReturn(new SocialUserInfo("12345", "other@kakao.com"));
        when(socialConnectionRepository.existsByProviderAndProviderId(AuthProvider.KAKAO, "12345")).thenReturn(true);

        CustomException exception = assertThrows(CustomException.class,
                () -> accountSettingsService.connectSocialAccount(1L, AuthProvider.KAKAO, "token"));

        assertEquals(ErrorCode.AUTH_017, exception.getErrorCode());
        verify(socialConnectionRepository, never()).saveAndFlush(any());
    }

    @Test
    void 저장_시점에_다른_회원이_먼저_연결해서_경쟁에_지면_AUTH_017을_던진다() {
        Member member = localMember();
        setId(member, 1L);
        when(memberRepository.findActiveById(1L)).thenReturn(Optional.of(member));
        when(memberRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(member));
        when(socialConnectionRepository.existsByMember_IdAndProvider(1L, AuthProvider.KAKAO)).thenReturn(false);
        when(socialUserInfoProviderRegistry.get(AuthProvider.KAKAO)).thenReturn(kakaoProvider);
        when(kakaoProvider.fetchUserInfo("token")).thenReturn(new SocialUserInfo("12345", "social@kakao.com"));
        when(socialConnectionRepository.existsByProviderAndProviderId(AuthProvider.KAKAO, "12345")).thenReturn(false);
        when(socialConnectionRepository.saveAndFlush(any()))
                .thenThrow(constraintViolation("uk_social_connection_provider_provider_id"));

        Member otherMember = localMember();
        setId(otherMember, 99L);
        SocialConnection winnerConnection = SocialConnection.create(otherMember, AuthProvider.KAKAO, "12345");
        when(socialConnectionRepository.findByProviderAndProviderId(AuthProvider.KAKAO, "12345"))
                .thenReturn(Optional.of(winnerConnection));

        CustomException exception = assertThrows(CustomException.class,
                () -> accountSettingsService.connectSocialAccount(1L, AuthProvider.KAKAO, "token"));

        assertEquals(ErrorCode.AUTH_017, exception.getErrorCode());
    }

    @Test
    void 본인이_같은_소셜계정을_동시에_연결시도해서_경쟁에_지면_제약이름과_무관하게_AUTH_018을_던진다() {
        // DB가 provider+providerId 제약 위반만 보고해도, 실제로는 본인이 먼저 연결에 성공한
        // 것이라면(동시에 같은 소셜 계정을 두 번 연결 시도) "다른 계정에 연결됨"이 아니라
        // "이미 연결됨"으로 정확히 매핑돼야 한다.
        Member member = localMember();
        setId(member, 1L);
        when(memberRepository.findActiveById(1L)).thenReturn(Optional.of(member));
        when(memberRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(member));
        when(socialConnectionRepository.existsByMember_IdAndProvider(1L, AuthProvider.KAKAO)).thenReturn(false);
        when(socialUserInfoProviderRegistry.get(AuthProvider.KAKAO)).thenReturn(kakaoProvider);
        when(kakaoProvider.fetchUserInfo("token")).thenReturn(new SocialUserInfo("12345", "social@kakao.com"));
        when(socialConnectionRepository.existsByProviderAndProviderId(AuthProvider.KAKAO, "12345")).thenReturn(false);
        when(socialConnectionRepository.saveAndFlush(any()))
                .thenThrow(constraintViolation("uk_social_connection_provider_provider_id"));

        SocialConnection winnerConnection = SocialConnection.create(member, AuthProvider.KAKAO, "12345");
        when(socialConnectionRepository.findByProviderAndProviderId(AuthProvider.KAKAO, "12345"))
                .thenReturn(Optional.of(winnerConnection));

        CustomException exception = assertThrows(CustomException.class,
                () -> accountSettingsService.connectSocialAccount(1L, AuthProvider.KAKAO, "token"));

        assertEquals(ErrorCode.AUTH_018, exception.getErrorCode());
    }

    @Test
    void member_provider_제약_위반이면_조회_없이_바로_AUTH_018을_던진다() {
        Member member = localMember();
        setId(member, 1L);
        when(memberRepository.findActiveById(1L)).thenReturn(Optional.of(member));
        when(memberRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(member));
        when(socialConnectionRepository.existsByMember_IdAndProvider(1L, AuthProvider.KAKAO)).thenReturn(false);
        when(socialUserInfoProviderRegistry.get(AuthProvider.KAKAO)).thenReturn(kakaoProvider);
        when(kakaoProvider.fetchUserInfo("token")).thenReturn(new SocialUserInfo("12345", "social@kakao.com"));
        when(socialConnectionRepository.existsByProviderAndProviderId(AuthProvider.KAKAO, "12345")).thenReturn(false);
        when(socialConnectionRepository.saveAndFlush(any()))
                .thenThrow(constraintViolation("uk_social_connection_member_provider"));

        CustomException exception = assertThrows(CustomException.class,
                () -> accountSettingsService.connectSocialAccount(1L, AuthProvider.KAKAO, "token"));

        assertEquals(ErrorCode.AUTH_018, exception.getErrorCode());
        verify(socialConnectionRepository, never()).findByProviderAndProviderId(any(), any());
    }

    @Test
    void 소셜_연결을_정상적으로_해제한다() {
        Member member = localMember();
        setId(member, 1L);
        when(memberRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(member));
        SocialConnection connection = SocialConnection.create(member, AuthProvider.KAKAO, "12345");
        when(socialConnectionRepository.findByMember_IdAndProvider(1L, AuthProvider.KAKAO))
                .thenReturn(Optional.of(connection));

        accountSettingsService.disconnectSocialAccount(1L, AuthProvider.KAKAO);

        verify(socialConnectionRepository).delete(connection);
        verify(socialConnectionRepository, never()).countByMember_Id(any());
    }

    @Test
    void 비밀번호가_있으면_마지막_소셜연결도_해제할_수_있다() {
        Member member = localMember(); // 비밀번호 보유
        setId(member, 1L);
        when(memberRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(member));
        SocialConnection connection = SocialConnection.create(member, AuthProvider.KAKAO, "12345");
        when(socialConnectionRepository.findByMember_IdAndProvider(1L, AuthProvider.KAKAO))
                .thenReturn(Optional.of(connection));

        accountSettingsService.disconnectSocialAccount(1L, AuthProvider.KAKAO);

        verify(socialConnectionRepository).delete(connection);
        verify(socialConnectionRepository, never()).countByMember_Id(any());
    }

    @Test
    void 비밀번호가_없고_마지막_남은_소셜연결이면_AUTH_020_예외를_던진다() {
        Member member = socialMember(); // 비밀번호 없음
        setId(member, 1L);
        when(memberRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(member));
        SocialConnection connection = SocialConnection.create(member, AuthProvider.KAKAO, "12345");
        when(socialConnectionRepository.findByMember_IdAndProvider(1L, AuthProvider.KAKAO))
                .thenReturn(Optional.of(connection));
        when(socialConnectionRepository.countByMember_Id(1L)).thenReturn(1L);

        CustomException exception = assertThrows(CustomException.class,
                () -> accountSettingsService.disconnectSocialAccount(1L, AuthProvider.KAKAO));

        assertEquals(ErrorCode.AUTH_020, exception.getErrorCode());
        verify(socialConnectionRepository, never()).delete(any());
    }

    @Test
    void 연결돼있지_않은_provider를_해제하려하면_AUTH_019_예외를_던진다() {
        Member member = localMember();
        setId(member, 1L);
        when(memberRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(member));
        when(socialConnectionRepository.findByMember_IdAndProvider(1L, AuthProvider.KAKAO))
                .thenReturn(Optional.empty());

        CustomException exception = assertThrows(CustomException.class,
                () -> accountSettingsService.disconnectSocialAccount(1L, AuthProvider.KAKAO));

        assertEquals(ErrorCode.AUTH_019, exception.getErrorCode());
    }

    private DataIntegrityViolationException constraintViolation(String constraintName) {
        ConstraintViolationException cause = new ConstraintViolationException(
                "duplicate key", new SQLException("duplicate"), constraintName);
        return new DataIntegrityViolationException("constraint violated", cause);
    }
}
