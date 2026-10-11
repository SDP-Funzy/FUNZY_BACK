package com.sdp1617.backend.notification.service;

import com.sdp1617.backend.auth.repository.MemberRepository;
import com.sdp1617.backend.auth.service.AllSessionsRevokedEvent;
import com.sdp1617.backend.global.config.properties.PushProperties;
import com.sdp1617.backend.global.error.CustomException;
import com.sdp1617.backend.global.error.ErrorCode;
import com.sdp1617.backend.notification.dto.PushPublicKeyResponse;
import com.sdp1617.backend.notification.dto.PushSubscriptionRequest;
import com.sdp1617.backend.notification.entity.PushSubscription;
import com.sdp1617.backend.notification.push.EcKeys;
import com.sdp1617.backend.notification.push.PushEndpointPolicy;
import com.sdp1617.backend.notification.repository.PushSubscriptionRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 웹 푸시 구독 (#89). 브라우저마다 구독이 하나씩 생기고, 회원은 여러 브라우저(기기)에서 구독할 수 있다.
 * 로그아웃 전에는 프론트가 그 브라우저의 구독을 해제하고, 모든 기기에서 로그아웃될 때(비밀번호 변경·재설정, 잠금 해제, 탈퇴)는
 * 회원의 구독을 모두 지운다 — 다시 로그인한 브라우저에서 새로 등록한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PushSubscriptionService {

    /** 회원당 구독 수. 넘으면 오래전에 등록한 브라우저부터 지운다. */
    static final int MAX_SUBSCRIPTIONS_PER_MEMBER = 10;
    private static final int AUTH_SECRET_BYTES = 16;

    private final PushSubscriptionRepository pushSubscriptionRepository;
    private final PushProperties pushProperties;
    private final MemberRepository memberRepository;

    public PushPublicKeyResponse getPublicKey() {
        return new PushPublicKeyResponse(pushProperties.enabled() ? pushProperties.vapidPublicKey() : null);
    }

    /**
     * 구독 등록·갱신. 같은 브라우저(구독 주소)로 다시 등록하면 키를 갱신하고, 다른 회원이었으면 이 회원으로 옮긴다.
     * 회원 행을 잠가 같은 회원의 등록을 순서대로 처리한다 — 같은 브라우저의 탭들이나 앱 열기·로그인이 동시에 처음 등록해도
     * 뒤의 요청은 앞에서 저장한 구독을 갱신하므로, 구독 주소 중복으로 실패하지 않는다.
     */
    @Transactional
    public void subscribe(Long memberId, PushSubscriptionRequest request) {
        String endpoint = request.endpoint().trim();
        String p256dh = request.keys().p256dh().trim();
        String auth = request.keys().auth().trim();
        validate(endpoint, p256dh, auth);
        memberRepository.findActiveByIdForUpdate(memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.AUTH_002));

        pushSubscriptionRepository.findByEndpoint(endpoint).ifPresentOrElse(
                subscription -> subscription.renew(memberId, p256dh, auth),
                () -> pushSubscriptionRepository.save(new PushSubscription(memberId, endpoint, p256dh, auth)));
        pushSubscriptionRepository.flush();

        List<PushSubscription> subscriptions = pushSubscriptionRepository.findByMemberIdOrderByUpdatedAtDesc(memberId);
        if (subscriptions.size() > MAX_SUBSCRIPTIONS_PER_MEMBER) {
            pushSubscriptionRepository.deleteAll(
                    subscriptions.subList(MAX_SUBSCRIPTIONS_PER_MEMBER, subscriptions.size()));
        }
    }

    /** 이 브라우저의 구독 해제. 내 구독이 아니거나 이미 없으면 아무것도 하지 않는다. */
    @Transactional
    public void unsubscribe(Long memberId, String endpoint) {
        pushSubscriptionRepository.deleteByMemberIdAndEndpoint(memberId, endpoint.trim());
    }

    @EventListener
    @Transactional
    public void onAllSessionsRevoked(AllSessionsRevokedEvent event) {
        pushSubscriptionRepository.deleteByMemberId(event.memberId());
    }

    private void validate(String endpoint, String p256dh, String auth) {
        if (!PushEndpointPolicy.isAllowed(endpoint, pushProperties.allowedEndpointHosts())) {
            throw new CustomException(ErrorCode.NOTIFICATION_002);
        }
        try {
            EcKeys.decodePublicKey(EcKeys.decodeBase64Url(p256dh));
            if (EcKeys.decodeBase64Url(auth).length != AUTH_SECRET_BYTES) {
                throw new CustomException(ErrorCode.NOTIFICATION_002);
            }
        } catch (IllegalArgumentException e) {
            throw new CustomException(ErrorCode.NOTIFICATION_002);
        }
    }
}
