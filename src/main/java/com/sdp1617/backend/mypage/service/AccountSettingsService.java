package com.sdp1617.backend.mypage.service;

import com.sdp1617.backend.auth.entity.AuthProvider;
import com.sdp1617.backend.auth.entity.Member;
import com.sdp1617.backend.auth.entity.SocialConnection;
import com.sdp1617.backend.auth.repository.MemberRepository;
import com.sdp1617.backend.auth.repository.SocialConnectionRepository;
import com.sdp1617.backend.auth.service.AllSessionsRevokedEvent;
import com.sdp1617.backend.auth.service.TokenService;
import com.sdp1617.backend.auth.social.SocialUserInfo;
import com.sdp1617.backend.auth.social.SocialUserInfoProviderRegistry;
import com.sdp1617.backend.global.error.ConstraintViolations;
import com.sdp1617.backend.global.error.CustomException;
import com.sdp1617.backend.global.error.ErrorCode;
import com.sdp1617.backend.mypage.dto.ConnectedAccountResponse;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
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
public class AccountSettingsService {

    private final MemberRepository memberRepository;
    private final SocialConnectionRepository socialConnectionRepository;
    private final SocialUserInfoProviderRegistry socialUserInfoProviderRegistry;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate transactionTemplate;

    public ConnectedAccountResponse getConnectedAccount(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.AUTH_002));

        List<AuthProvider> connectedProviders = socialConnectionRepository.findByMember_Id(memberId).stream()
                .map(SocialConnection::getProvider)
                .toList();

        return new ConnectedAccountResponse(connectedProviders, member.hasPassword());
    }

    @Transactional
    public void changePassword(Long memberId, String currentPassword, String newPassword, String newPasswordConfirm) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.AUTH_002));

        if (!member.hasPassword()) {
            throw new CustomException(ErrorCode.AUTH_014);
        }
        if (!passwordEncoder.matches(currentPassword, member.getPassword())) {
            throw new CustomException(ErrorCode.AUTH_015);
        }
        if (!newPassword.equals(newPasswordConfirm)) {
            throw new CustomException(ErrorCode.AUTH_008);
        }

        member.changePassword(passwordEncoder.encode(newPassword));
        eventPublisher.publishEvent(new AllSessionsRevokedEvent(memberId));
    }

    @Transactional
    public void logout(Long memberId, String refreshToken) {
        tokenService.revokeSession(memberId, refreshToken);
    }

    @Transactional
    public void withdraw(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.AUTH_002));

        memberRepository.delete(member);
        eventPublisher.publishEvent(new AllSessionsRevokedEvent(memberId));
    }

    /**
     * 로그인된 본인 계정에 새 소셜 provider를 추가로 연결한다. 이메일이 같다는 이유로 다른 회원의
     * SocialConnection에 자동으로 병합하지 않는다(계정 탈취 방지) — 항상 memberId(현재 로그인된
     * 회원)를 연결 대상으로 고정한다.
     * 이 메서드 자체는 트랜잭션을 걸지 않는다(NOT_SUPPORTED) — 소셜 제공자로의 외부 HTTP 호출
     * (fetchUserInfo)이 오래 걸리는 동안 DB 커넥션을 붙잡고 있지 않기 위해서다(#45의 S3 검증과
     * 동일한 이유). 실제 쓰기는 TransactionTemplate으로 별도의 짧은 트랜잭션에 격리한다.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void connectSocialAccount(Long memberId, AuthProvider provider, String token) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.AUTH_002));

        if (socialConnectionRepository.existsByMember_IdAndProvider(memberId, provider)) {
            throw new CustomException(ErrorCode.AUTH_018);
        }

        SocialUserInfo userInfo = socialUserInfoProviderRegistry.get(provider).fetchUserInfo(token);

        if (socialConnectionRepository.existsByProviderAndProviderId(provider, userInfo.externalId())) {
            throw new CustomException(ErrorCode.AUTH_017);
        }

        try {
            // saveAndFlush로 커밋을 기다리지 않고 바로 제약 위반을 드러낸다 — 위 두 exists 체크와
            // 실제 저장 사이의 경쟁(같은 소셜 계정을 동시에 연결 시도)에 대한 최종 방어선.
            transactionTemplate.executeWithoutResult(status ->
                    socialConnectionRepository.saveAndFlush(SocialConnection.create(member, provider, userInfo.externalId())));
        } catch (DataIntegrityViolationException exception) {
            throw new CustomException(
                    resolveConnectionConflict(exception, memberId, provider, userInfo.externalId())
                            .orElseThrow(() -> exception));
        }
    }

    /**
     * 연결된 소셜 provider 하나를 해제한다. 비밀번호가 없고(소셜 전용 계정) 연결된 소셜이 이것
     * 하나뿐이면, 해제 시 로그인할 방법이 아예 없어지므로 거부한다(AUTH_020).
     * 서로 다른 provider에 대한 해제 요청이 동시에 들어오는 경쟁을 막기 위해 회원 행에 비관적
     * 쓰기 락을 걸어 직렬화한다 — {@link MemberRepository#findByIdForUpdate} 참고.
     */
    @Transactional
    public void disconnectSocialAccount(Long memberId, AuthProvider provider) {
        Member member = memberRepository.findByIdForUpdate(memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.AUTH_002));

        SocialConnection connection = socialConnectionRepository.findByMember_IdAndProvider(memberId, provider)
                .orElseThrow(() -> new CustomException(ErrorCode.AUTH_019));

        if (!member.hasPassword() && socialConnectionRepository.countByMember_Id(memberId) <= 1) {
            throw new CustomException(ErrorCode.AUTH_020);
        }

        socialConnectionRepository.delete(connection);
    }

    /**
     * 본인이 같은 소셜 계정을 동시에 두 번 연결 시도하면 uk_social_connection_provider_provider_id와
     * uk_social_connection_member_provider가 같은 insert 때문에 동시에 위반되는데, DB는 그중 하나의
     * 제약 이름만 보고한다. 하필 provider_provider_id 쪽이 보고되면 제약 이름만으로는 "본인과의
     * 경쟁"과 "다른 회원과의 경쟁"을 구분할 수 없으므로, 실제 기존 연결을 다시 조회해 회원을 비교한다.
     * member_provider 제약은 정의상 항상 이 memberId 자신에 대한 것이므로 그 경우는 바로 AUTH_018.
     */
    private Optional<ErrorCode> resolveConnectionConflict(
            DataIntegrityViolationException exception, Long memberId, AuthProvider provider, String externalId) {
        return ConstraintViolations.nameOf(exception).flatMap(name -> {
            if ("uk_social_connection_member_provider".equalsIgnoreCase(name)) {
                return Optional.of(ErrorCode.AUTH_018);
            }
            if ("uk_social_connection_provider_provider_id".equalsIgnoreCase(name)) {
                return Optional.of(socialConnectionRepository.findByProviderAndProviderId(provider, externalId)
                        .map(existing -> existing.getMember().getId().equals(memberId) ? ErrorCode.AUTH_018 : ErrorCode.AUTH_017)
                        .orElse(ErrorCode.AUTH_017));
            }
            return Optional.empty();
        });
    }
}
