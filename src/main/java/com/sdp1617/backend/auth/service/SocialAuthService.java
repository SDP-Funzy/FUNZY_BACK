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
import com.sdp1617.backend.global.error.ConstraintViolations;
import com.sdp1617.backend.global.error.CustomException;
import com.sdp1617.backend.global.error.ErrorCode;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SocialAuthService {

    private static final Duration SIGNUP_SESSION_TTL = Duration.ofMinutes(15);

    private final SocialUserInfoProviderRegistry providerRegistry;
    private final MemberRepository memberRepository;
    private final SocialConnectionRepository socialConnectionRepository;
    private final SocialSignupSessionRepository socialSignupSessionRepository;
    private final TokenService tokenService;
    private final TransactionTemplate transactionTemplate;

    /**
     * 소셜 로그인 여부 판단은 (이제 회원당 하나만 표현 가능한) Member.provider/providerId가 아니라,
     * 회원이 여러 소셜 수단을 동시에 연결할 수 있는 SocialConnection을 기준으로 한다.
     */
    public SocialAuthResponse login(AuthProvider provider, String token) {
        SocialUserInfoProvider userInfoProvider = providerRegistry.get(provider);
        SocialUserInfo userInfo = userInfoProvider.fetchUserInfo(token);

        return socialConnectionRepository.findByProviderAndProviderId(provider, userInfo.externalId())
                .map(SocialConnection::getMember)
                .map(member -> SocialAuthResponse.existingUser(
                        tokenService.issueTokens(member.getId(), member.getSessionVersion())))
                .orElseGet(() -> startSignup(provider, userInfo));
    }

    private SocialAuthResponse startSignup(AuthProvider provider, SocialUserInfo userInfo) {
        if (userInfo.email() != null && memberRepository.existsByEmail(userInfo.email())) {
            throw new CustomException(ErrorCode.AUTH_012);
        }

        SocialSignupSession session = new SocialSignupSession(provider, userInfo.externalId(), userInfo.email());
        String signupToken = socialSignupSessionRepository.issue(session, SIGNUP_SESSION_TTL);
        return SocialAuthResponse.newUser(signupToken, userInfo.email());
    }

    /**
     * 이 메서드 자체는 트랜잭션을 걸지 않는다(NOT_SUPPORTED) — DB 쓰기는
     * {@link #createMemberWithConnection}에서 별도의 짧은 트랜잭션으로 격리해서 처리한다.
     * completeSignUp 전체를 하나의 @Transactional로 두면, 동시에 같은 소셜 계정으로 signup을
     * 완료하려는 경쟁에서 saveAndFlush가 던지는 제약 위반을 여기서 catch해도 이미 늦다 —
     * saveAndFlush 자신의 Spring Data 트랜잭션 프록시가 예외가 밖으로 나가는 순간 이 메서드가
     * 참여 중이던 트랜잭션을 rollback-only로 표시해버리기 때문에, 이후 issueTokens까지 정상
     * 실행되고 메서드가 정상 반환돼도 커밋 시점에 UnexpectedRollbackException이 터진다 —
     * 그것도 issueTokens가 Redis에 refresh token을 이미 써넣은 뒤에. 그래서 Member/SocialConnection
     * 생성만 별도 물리 트랜잭션으로 완전히 끝낸 뒤에야 폴백 조회/토큰 발급으로 넘어가야 안전하다.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public TokenResponse completeSignUp(String signupToken, String nickname, Consent consent) {
        SocialSignupSession session = socialSignupSessionRepository.consume(signupToken)
                .orElseThrow(() -> new CustomException(ErrorCode.AUTH_011));

        if (memberRepository.isNicknameTaken(nickname)) {
            throw new CustomException(ErrorCode.AUTH_007);
        }
        if (session.email() != null && memberRepository.existsByEmail(session.email())) {
            throw new CustomException(ErrorCode.AUTH_012);
        }

        Long memberId = createMemberWithConnection(session, nickname, consent);
        return tokenService.issueTokens(memberId, Member.INITIAL_SESSION_VERSION);
    }

    private Long createMemberWithConnection(SocialSignupSession session, String nickname, Consent consent) {
        try {
            return transactionTemplate.execute(status -> {
                Member member = new Member(session.email(), nickname, consent, session.provider(), session.externalId());
                memberRepository.saveWithNicknameUniqueness(member);
                // saveAndFlush로 커밋을 기다리지 않고 바로 제약 위반을 드러낸다 — save만 쓰면 위반이
                // 이 트랜잭션의 커밋 시점(콜백 반환 이후)에야 터져서 제때 잡아낼 수 없다.
                socialConnectionRepository.saveAndFlush(
                        SocialConnection.create(member, session.provider(), session.externalId()));
                return member.getId();
            });
        } catch (DataIntegrityViolationException exception) {
            if (!violatesProviderConnectionConstraint(exception)) {
                throw exception;
            }
            // 같은 소셜 계정으로 동시에 signup을 완료하려던 경쟁에서 진 쪽 — 위 트랜잭션은 이미
            // 롤백되어 끝났고(격리됨), 다른 요청이 먼저 만든 계정으로 로그인시켜준다.
            return socialConnectionRepository.findByProviderAndProviderId(session.provider(), session.externalId())
                    .map(connection -> connection.getMember().getId())
                    .orElseThrow(() -> exception);
        }
    }

    private boolean violatesProviderConnectionConstraint(DataIntegrityViolationException exception) {
        return ConstraintViolations.nameOf(exception)
                .filter(name -> "uk_member_provider_provider_id".equalsIgnoreCase(name)
                        || "uk_social_connection_provider_provider_id".equalsIgnoreCase(name))
                .isPresent();
    }
}
